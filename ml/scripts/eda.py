import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
from sklearn.decomposition import PCA

# 1. Primer llegim les dades (abans de fer res amb elles)
df_x = pd.read_csv('../data/raw/total_acc_x_train.txt', sep=r'\s+', header=None)

# 2. Ara que df_x existeix, ja podem fer el PCA
pca = PCA(n_components=2)
components = pca.fit_transform(df_x.head(500)) 

plt.figure(figsize=(8, 6))
plt.scatter(components[:, 0], components[:, 1])
plt.title("Visualització PCA (Dades acceleròmetre)")
plt.savefig('../data/processed/pca_visualization.png')
# plt.close() # Tanca la figura per no saturar memòria

# 3. Visualització ràpida del senyal (primera fila)
plt.figure(figsize=(10, 4))
plt.plot(df_x.iloc[0])
plt.title("Senyal d'acceleració X (Mostra 1)")
plt.savefig('../data/processed/signal_sample.png')

# 4. Histogrames de les etiquetes
y_train = pd.read_csv('../data/raw/y_train.txt', header=None)
y_train.columns = ['label'] 
sns.countplot(x='label', data=y_train) 
plt.title("Distribució de classes")
plt.savefig('../data/processed/class_distribution.png')
