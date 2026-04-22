import tensorflow as tf
from tensorflow.keras import layers, models
import numpy as np

# 1. Carregar dades (suposem que X_train, y_train estan en .npy)
X_train = np.load('../data/processed/X_train.npy')
y_train = np.load('../data/processed/y_train.npy')

# 2. Definir model CNN 1D
model = models.Sequential([
    layers.Conv1D(filters=64, kernel_size=3, activation='relu', input_shape=(128, 3)),
    layers.MaxPooling1D(pool_size=2),
    layers.Flatten(),
    layers.Dense(64, activation='relu'),
    layers.Dense(6, activation='softmax') # 6 classes (UCI HAR)
])

model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])

# 3. Entrenar
model.fit(X_train, y_train, epochs=10, batch_size=32)

# 4. Guardar model
model.save('../models/model_cnn.h5')

# 5. Convertir a TFLite (IMPORTANT!)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open('../models/model_v1.tflite', 'wb') as f:
    f.write(tflite_model)