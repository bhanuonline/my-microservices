from fastapi import FastAPI
import joblib

app = FastAPI()

model = joblib.load("cod_model.pkl")

@app.post("/predict")
def predict(data: dict):
    features = [[data["order_value"], data["previous_returns"]]]
    prediction = model.predict(features)
    return {"prediction": int(prediction[0])}