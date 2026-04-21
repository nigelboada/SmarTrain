import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

# Carregar dades (exemple per al teu CSV)
df = pd.read_csv('../data/raw/train/Inertial Signals/total_acc_x_train.txt', sep='\s+', header=None)

# 1. Visualització ràpida del senyal (primera fila)
plt.figure(figsize=(10, 4))
plt.plot(df.iloc[0])
plt.title("Senyal d'acceleració (Mostra 1)")
plt.savefig('../data/processed/signal_sample.png')
plt.show()

# 2. Histogrames de les etiquetes
# Això requereix carregar y_train també
y_train = pd.read_csv('../data/raw/train/y_train.txt', header=None)
sns.countplot(x=0, data=y_train)
plt.title("Distribució de classes")
plt.savefig('../data/processed/class_distribution.png')
plt.show()