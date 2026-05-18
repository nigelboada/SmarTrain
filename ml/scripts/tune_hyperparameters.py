"""
Ajust d'hiperparametres del model HAR de SmarTrain.

Execucio recomanada des de l'arrel del projecte:
    python ml/scripts/tune_hyperparameters.py

Sortides:
    ml/results/hyperparameter_tuning.json
    ml/results/hyperparameter_tuning.csv
    ml/models/model_tuned_candidate.tflite
"""

from __future__ import annotations

import csv
import json
import time
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from tensorflow.keras import layers, models


ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data" / "processed"
RESULTS_DIR = ROOT / "results"
MODELS_DIR = ROOT / "models"
LABELS = ["Caminar", "Pujar escales", "Baixar escales", "Seure", "Dret", "Estirat"]
RANDOM_STATE = 42


@dataclass(frozen=True)
class TrialConfig:
    name: str
    filters_1: int
    filters_2: int
    dense_units: int
    dropout: float
    learning_rate: float
    batch_size: int


TRIALS = [
    TrialConfig("small_lr_1e3_do30_b32", 32, 64, 64, 0.30, 1e-3, 32),
    TrialConfig("small_lr_5e4_do30_b32", 32, 64, 64, 0.30, 5e-4, 32),
    TrialConfig("medium_lr_1e3_do35_b32", 48, 96, 80, 0.35, 1e-3, 32),
    TrialConfig("medium_lr_5e4_do35_b64", 48, 96, 80, 0.35, 5e-4, 64),
    TrialConfig("deep_lr_1e3_do40_b32", 64, 128, 96, 0.40, 1e-3, 32),
    TrialConfig("deep_lr_5e4_do40_b64", 64, 128, 96, 0.40, 5e-4, 64),
]


def load_data() -> tuple[np.ndarray, np.ndarray]:
    x = np.load(DATA_DIR / "X_train.npy").astype("float32")
    y = np.load(DATA_DIR / "y_train.npy").astype("int64")
    return x, y


def split_data(x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, ...]:
    x_train, x_temp, y_train, y_temp = train_test_split(
        x,
        y,
        test_size=0.30,
        random_state=RANDOM_STATE,
        stratify=y,
    )
    x_val, x_test, y_val, y_test = train_test_split(
        x_temp,
        y_temp,
        test_size=0.50,
        random_state=RANDOM_STATE,
        stratify=y_temp,
    )
    return x_train, x_val, x_test, y_train, y_val, y_test


def build_model(config: TrialConfig) -> tf.keras.Model:
    model = models.Sequential(
        [
            layers.Input(shape=(128, 3)),
            layers.Conv1D(config.filters_1, kernel_size=5, activation="relu"),
            layers.BatchNormalization(),
            layers.MaxPooling1D(pool_size=2),
            layers.Conv1D(config.filters_2, kernel_size=3, activation="relu"),
            layers.BatchNormalization(),
            layers.GlobalAveragePooling1D(),
            layers.Dense(config.dense_units, activation="relu"),
            layers.Dropout(config.dropout),
            layers.Dense(len(LABELS), activation="softmax"),
        ]
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=config.learning_rate),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def run_trial(config: TrialConfig, data: tuple[np.ndarray, ...]) -> tuple[dict, tf.keras.Model]:
    tf.keras.backend.clear_session()
    tf.keras.utils.set_random_seed(RANDOM_STATE)
    x_train, x_val, x_test, y_train, y_val, y_test = data
    model = build_model(config)
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=3,
            restore_best_weights=True,
        )
    ]

    started = time.perf_counter()
    history = model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=18,
        batch_size=config.batch_size,
        callbacks=callbacks,
        verbose=0,
    )
    training_seconds = time.perf_counter() - started

    val_probabilities = model.predict(x_val, verbose=0)
    test_probabilities = model.predict(x_test, verbose=0)
    val_pred = val_probabilities.argmax(axis=1)
    test_pred = test_probabilities.argmax(axis=1)

    return {
        **asdict(config),
        "epochs_ran": len(history.history["loss"]),
        "best_val_loss": float(min(history.history["val_loss"])),
        "best_val_accuracy": float(max(history.history["val_accuracy"])),
        "val_accuracy": float(accuracy_score(y_val, val_pred)),
        "val_weighted_f1": float(f1_score(y_val, val_pred, average="weighted")),
        "test_accuracy": float(accuracy_score(y_test, test_pred)),
        "test_weighted_f1": float(f1_score(y_test, test_pred, average="weighted")),
        "training_seconds": float(training_seconds),
    }, model


def export_tflite(model: tf.keras.Model, output_path: Path) -> bytes:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    output_path.write_bytes(tflite_model)
    return tflite_model


def benchmark_tflite(tflite_model: bytes, sample: np.ndarray, runs: int = 100) -> float:
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    sample = sample.astype(input_details[0]["dtype"])
    interpreter.set_tensor(input_details[0]["index"], sample)
    interpreter.invoke()

    started = time.perf_counter()
    for _ in range(runs):
        interpreter.set_tensor(input_details[0]["index"], sample)
        interpreter.invoke()
        interpreter.get_tensor(output_details[0]["index"])
    elapsed = time.perf_counter() - started
    return (elapsed / runs) * 1000


def write_csv(rows: list[dict], path: Path) -> None:
    if not rows:
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    RESULTS_DIR.mkdir(exist_ok=True)
    MODELS_DIR.mkdir(exist_ok=True)
    data = split_data(*load_data())
    x_test = data[2]
    rows = []
    trained_models = {}
    for config in TRIALS:
        print(f"Ajustant {config.name}...")
        row, model = run_trial(config, data)
        rows.append(row)
        trained_models[config.name] = model

    selected = sorted(
        rows,
        key=lambda item: (
            -item["val_weighted_f1"],
            item["best_val_loss"],
            item["training_seconds"],
        ),
    )[0]
    tuned_model_path = MODELS_DIR / "model_tuned_candidate.tflite"
    tuned_tflite = export_tflite(trained_models[selected["name"]], tuned_model_path)
    selected = {
        **selected,
        "tflite_path": str(tuned_model_path.relative_to(ROOT.parent)),
        "tflite_size_bytes": tuned_model_path.stat().st_size,
        "tflite_inference_ms": benchmark_tflite(tuned_tflite, x_test[:1]),
    }
    output = {
        "description": "Ajust d'hiperparametres CNN 1D sobre UCI HAR per SmarTrain.",
        "selection_metric": "val_weighted_f1, desempat per val_loss i temps d'entrenament",
        "labels": LABELS,
        "trials": rows,
        "selected_trial": selected,
    }
    (RESULTS_DIR / "hyperparameter_tuning.json").write_text(
        json.dumps(output, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    write_csv(rows, RESULTS_DIR / "hyperparameter_tuning.csv")
    print(json.dumps(output, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
