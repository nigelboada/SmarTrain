import pandas as pd
import numpy as np
import os

def create_windows(data, window_size=50, stride=25):
    """
    Divideix les dades en finestres temporals (windowing).
    A 50Hz, 50 mostres = 1 segon.
    """
    windows = []
    labels = []

    # Suposem que les dades tenen columnes 'accX', 'accY', 'accZ' i 'label'
    for i in range(0, len(data) - window_size, stride):
        window = data.iloc[i:i + window_size][['accX', 'accY', 'accZ']].values
        # Agafem la moda de les etiquetes en aquesta finestra
        label = data.iloc[i:i + window_size]['label'].mode()[0]
        windows.append(window)
        labels.append(label)

    return np.array(windows), np.array(labels)

# 1. Carregar dades raw
raw_data_path = '../data/raw/raw_sensor_data.csv'
if os.path.exists(raw_data_path):
    df = pd.read_csv(raw_data_path)

    # 2. Processar
    X, y = create_windows(df)

    # 3. Guardar per a l'entrenament
    np.save('../data/processed/X_data.npy', X)
    np.save('../data/processed/y_data.npy', y)
    print(f"Dades processades amb èxit. Forma: {X.shape}")
else:
    print("Error: No s'ha trobat el fitxer de dades raw.")