import pandas as pd
import numpy as np
import os

# Ruta base del dataset
base_path = '../data/raw/train/Inertial Signals'

def load_data():
    # Carreguem els 3 eixos de l'acceleròmetre
    acc_x = pd.read_csv(os.path.join(base_path, 'total_acc_x_train.txt'), sep=r'\s+', header=None).values
    acc_y = pd.read_csv(os.path.join(base_path, 'total_acc_y_train.txt'), sep=r'\s+', header=None).values
    acc_z = pd.read_csv(os.path.join(base_path, 'total_acc_z_train.txt'), sep=r'\s+', header=None).values
    
    # Carreguem les etiquetes (y_train)
    y = pd.read_csv('../data/raw/train/y_train.txt', header=None).values.flatten() - 1 # Restem 1 per a que comencin a 0
    
    # Apilem els eixos per tenir una forma (samples, 128, 3)
    # np.stack ens permet posar els 3 arrays junts
    X = np.stack([acc_x, acc_y, acc_z], axis=2)
    
    return X, y

# Execució
print("Processant dades UCI HAR...")
X, y = load_data()

# Guardar
np.save('../data/processed/X_train.npy', X)
np.save('../data/processed/y_train.npy', y)

print(f"Dades processades correctament!")
print(f"Forma de X: {X.shape}") # Hauria de ser (7352, 128, 3)
print(f"Forma de y: {y.shape}")