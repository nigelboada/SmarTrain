import os
import pandas as pd
import numpy as np
from tensorflow.keras import layers, models
from sklearn.metrics import classification_report

def load_dataset(base_path, type="train"):
    x_path = os.path.join(base_path, type, f"X_{type}.txt")
    y_path = os.path.join(base_path, type, f"y_{type}.txt")

    X = pd.read_csv(x_path, sep=r'\s+', header=None)
    y = pd.read_csv(y_path, sep=r'\s+', header=None)

    X_reshaped = X.values.reshape((X.shape[0], X.shape[1], 1))
    return X_reshaped, y.values.ravel() - 1 # Ajustem etiquetes a 0-5

def run_cnn_experiment():
    data_dir = "UCI HAR Dataset"
    if not os.path.exists(data_dir):
        print("Error: No trobo el dataset. Executa primer train_baseline.py")
        return

    X_train, y_train = load_dataset(data_dir, "train")
    X_test, y_test = load_dataset(data_dir, "test")

    model = models.Sequential([
        layers.Conv1D(filters=64, kernel_size=3, activation='relu', input_shape=(X_train.shape[1], 1)),
        layers.Dropout(0.5),
        layers.MaxPooling1D(pool_size=2),
        layers.Flatten(),
        layers.Dense(100, activation='relu'),
        layers.Dense(6, activation='softmax') # 6 classes d'activitat
    ])

    model.compile(optimizer='adam',
                  loss='sparse_categorical_crossentropy',
                  metrics=['accuracy'])

    print("Entrenant CNN 1D (Deep Learning)...")
    model.fit(X_train, y_train, epochs=10, batch_size=32, validation_split=0.1, verbose=1)

    print("\n--- RESULTATS EXPERIMENT 2 (CNN) ---")
    loss, acc = model.evaluate(X_test, y_test, verbose=0)
    y_pred = np.argmax(model.predict(X_test), axis=1)

    print(f"Accuracy Total: {acc:.4f}")
    print(classification_report(y_test, y_pred))

    model.save("ml/models/smartrain_cnn_v1.h5")
    print("Model guardat a ml/models/smartrain_cnn_v1.h5")

if __name__ == "__main__":
    run_cnn_experiment()