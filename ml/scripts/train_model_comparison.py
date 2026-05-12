"""
Compara models HAR per a SmarTrain i exporta el millor candidat a TFLite.

Execucio recomanada des de la carpeta `ml/scripts`:
    python train_model_comparison.py

Sortides generades:
    ml/results/model_comparison.json
    ml/results/confusion_matrix_final.png
    ml/models/model_v1.tflite
    app/src/main/assets/model_v1.tflite
"""

from __future__ import annotations

import json
import time
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.model_selection import train_test_split
from tensorflow.keras import layers, models


ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ROOT.parent
DATA_DIR = ROOT / "data" / "processed"
MODELS_DIR = ROOT / "models"
RESULTS_DIR = ROOT / "results"
APP_ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
LABELS = ["Caminar", "Pujar escales", "Baixar escales", "Seure", "Dret", "Estirat"]
RANDOM_STATE = 42


def load_data() -> tuple[np.ndarray, np.ndarray]:
    x = np.load(DATA_DIR / "X_train.npy").astype("float32")
    y = np.load(DATA_DIR / "y_train.npy").astype("int64")
    return x, y


def split_data(x: np.ndarray, y: np.ndarray):
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


def build_cnn_lite() -> tf.keras.Model:
    model = models.Sequential(
        [
            layers.Input(shape=(128, 3)),
            layers.Conv1D(32, kernel_size=5, activation="relu"),
            layers.MaxPooling1D(pool_size=2),
            layers.Conv1D(64, kernel_size=3, activation="relu"),
            layers.GlobalAveragePooling1D(),
            layers.Dense(48, activation="relu"),
            layers.Dropout(0.30),
            layers.Dense(len(LABELS), activation="softmax"),
        ]
    )
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def build_cnn_deep() -> tf.keras.Model:
    model = models.Sequential(
        [
            layers.Input(shape=(128, 3)),
            layers.Conv1D(64, kernel_size=5, activation="relu"),
            layers.BatchNormalization(),
            layers.MaxPooling1D(pool_size=2),
            layers.Conv1D(128, kernel_size=3, activation="relu"),
            layers.BatchNormalization(),
            layers.GlobalAveragePooling1D(),
            layers.Dense(96, activation="relu"),
            layers.Dropout(0.40),
            layers.Dense(len(LABELS), activation="softmax"),
        ]
    )
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def build_cnn_separable() -> tf.keras.Model:
    model = models.Sequential(
        [
            layers.Input(shape=(128, 3)),
            layers.SeparableConv1D(48, kernel_size=5, activation="relu"),
            layers.MaxPooling1D(pool_size=2),
            layers.SeparableConv1D(96, kernel_size=3, activation="relu"),
            layers.GlobalAveragePooling1D(),
            layers.Dense(64, activation="relu"),
            layers.Dropout(0.35),
            layers.Dense(len(LABELS), activation="softmax"),
        ]
    )
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def train_and_evaluate(name: str, model: tf.keras.Model, data: tuple[np.ndarray, ...]) -> dict:
    x_train, x_val, x_test, y_train, y_val, y_test = data
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=4,
            restore_best_weights=True,
        )
    ]

    started = time.perf_counter()
    history = model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=30,
        batch_size=32,
        callbacks=callbacks,
        verbose=2,
    )
    training_seconds = time.perf_counter() - started

    probabilities = model.predict(x_test, verbose=0)
    y_pred = probabilities.argmax(axis=1)

    return {
        "name": name,
        "model": model,
        "history": history.history,
        "accuracy": float(accuracy_score(y_test, y_pred)),
        "weighted_f1": float(f1_score(y_test, y_pred, average="weighted")),
        "training_seconds": float(training_seconds),
        "classification_report": classification_report(
            y_test,
            y_pred,
            target_names=LABELS,
            output_dict=True,
        ),
        "confusion_matrix": confusion_matrix(y_test, y_pred).tolist(),
    }


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


def save_confusion_matrix(matrix: list[list[int]], output_path: Path) -> None:
    plt.figure(figsize=(8, 6))
    plt.imshow(matrix, interpolation="nearest", cmap="Blues")
    plt.title("Matriu de confusio - model final")
    plt.colorbar()
    ticks = np.arange(len(LABELS))
    plt.xticks(ticks, LABELS, rotation=45, ha="right")
    plt.yticks(ticks, LABELS)
    plt.ylabel("Classe real")
    plt.xlabel("Prediccio")
    plt.tight_layout()
    plt.savefig(output_path)
    plt.close()


def main() -> None:
    MODELS_DIR.mkdir(exist_ok=True)
    RESULTS_DIR.mkdir(exist_ok=True)
    APP_ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    x, y = load_data()
    data = split_data(x, y)

    candidates = {
        "cnn_lite": build_cnn_lite(),
        "cnn_deep": build_cnn_deep(),
        "cnn_separable": build_cnn_separable(),
    }

    results = []
    for name, model in candidates.items():
        print(f"\n=== Entrenant {name} ===")
        result = train_and_evaluate(name, model, data)
        results.append(result)

    best = max(results, key=lambda item: item["weighted_f1"])
    final_model_path = MODELS_DIR / "model_v1.tflite"
    tflite_model = export_tflite(best["model"], final_model_path)
    (APP_ASSETS_DIR / "model_v1.tflite").write_bytes(tflite_model)
    inference_ms = benchmark_tflite(tflite_model, data[2][:1])
    save_confusion_matrix(best["confusion_matrix"], RESULTS_DIR / "confusion_matrix_final.png")

    serializable_results = []
    for result in results:
        serializable_results.append(
            {
                key: value
                for key, value in result.items()
                if key not in {"model", "history"}
            }
        )

    output = {
        "labels": LABELS,
        "selected_model": best["name"],
        "selected_model_size_bytes": final_model_path.stat().st_size,
        "selected_model_tflite_inference_ms": inference_ms,
        "results": serializable_results,
    }
    (RESULTS_DIR / "model_comparison.json").write_text(json.dumps(output, indent=2), encoding="utf-8")

    print("\nModel final:", best["name"])
    print("TFLite:", final_model_path)
    print(f"Inferencia mitjana: {inference_ms:.3f} ms")


if __name__ == "__main__":
    main()
