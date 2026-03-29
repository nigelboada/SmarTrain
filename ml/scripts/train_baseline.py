import os
import pandas as pd
import numpy as np
import urllib.request
import zipfile
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix

def download_data():
    url = "https://archive.ics.uci.edu/ml/machine-learning-databases/00240/UCI%20HAR%20Dataset.zip"
    filename = "uci_har_dataset.zip"
    if not os.path.exists(filename):
        print("Descarregant dataset UCI HAR...")
        urllib.request.urlretrieve(url, filename)
        with zipfile.ZipFile(filename, 'r') as zip_ref:
            zip_ref.extractall(".")
    return "UCI HAR Dataset"

def load_dataset(base_path, type="train"):
    # Carreguem les dades de l'acceleròmetre (X, Y, Z)
    # El dataset UCI ja ve segmentat en finestres de 128 mostres
    x_path = os.path.join(base_path, type, f"X_{type}.txt")
    y_path = os.path.join(base_path, type, f"y_{type}.txt")

    X = pd.read_csv(x_path, sep=r'\s+', header=None)
    y = pd.read_csv(y_path, sep=r'\s+', header=None)

    return X, y.values.ravel()

def run_experiment():
    data_dir = download_data()

    print("Preparant dades d'entrenament i test...")
    X_train, y_train = load_dataset(data_dir, "train")
    X_test, y_test = load_dataset(data_dir, "test")

    # CONFIGURACIÓ DEL PIPELINE: Random Forest
    # Utilitzem 100 arbres per tenir un bon equilibri precisió/mida
    print("Entrenant Random Forest Classifier...")
    model = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X_train, y_train)

    # RESULTATS
    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)

    print("\n--- RESULTATS EXPERIMENT 1 ---")
    print(f"Accuracy Total: {acc:.4f}")
    print("\nInforme detallat per activitat:")
    print(classification_report(y_test, y_pred))

if __name__ == "__main__":
    run_experiment()