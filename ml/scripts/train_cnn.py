import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models

# 1. Carregar dades processades
X_train = np.load('../data/processed/X_train.npy')
y_train = np.load('../data/processed/y_train.npy')

# 2. Definir la CNN 1D
model = models.Sequential([
    layers.Conv1D(filters=64, kernel_size=3, activation='relu', input_shape=(128, 3)),
    layers.MaxPooling1D(pool_size=2),
    layers.Flatten(),
    layers.Dense(64, activation='relu'),
    layers.Dropout(0.5), # Evitem sobreajust
    layers.Dense(6, activation='softmax') # 6 activitats (UCI HAR)
])

model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])

# 3. Entrenar
print("Començant l'entrenament...")
history = model.fit(X_train, y_train, epochs=15, batch_size=32, validation_split=0.2)

# 4. Guardar model i convertir a TFLite
model.save('../models/model_cnn.h5')

converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

with open('../models/model_v1.tflite', 'wb') as f:
    f.write(tflite_model)

print("Model TFLite generat amb èxit a /ml/models/model_v1.tflite")