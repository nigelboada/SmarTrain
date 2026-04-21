import tensorflow as tf
import pandas as pd
from sklearn.model_selection import train_test_split

# 1. Carregar dades
df = pd.read_csv('../data/processed/dades_entrenament.csv')
X = df.drop('label', axis=1).values
y = df['label'].values

# 2. Definir model senzill (Classificació)
model = tf.keras.Sequential([
    tf.keras.layers.Dense(64, activation='relu', input_shape=(X.shape[1],)),
    tf.keras.layers.Dense(32, activation='relu'),
    tf.keras.layers.Dense(3, activation='softmax') # Ex: Camina, Corre, Aturat
])

model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
model.fit(X, y, epochs=10)

# 3. CONVERSIÓ A TFLite (CRUCIAL per Android)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

with open('../models/model_v1.tflite', 'wb') as f:
    f.write(tflite_model)