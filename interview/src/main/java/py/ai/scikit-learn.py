#scikit-learn
import sklearn
print(sklearn.__version__)

from sklearn.linear_model import LinearRegression

model = LinearRegression()

X = [[1], [2], [3], [4]]
y = [2, 4, 6, 8]

model.fit(X, y)

print(model.predict([[10]]))

from sklearn.ensemble import RandomForestClassifier
import joblib

X = [[1000, 0], [2000, 3], [1500, 1], [5000, 5]]
y = [0, 1, 0, 1]  # 0 = low risk, 1 = high risk

model = RandomForestClassifier()
model.fit(X, y)

joblib.dump(model, "cod_model.pkl")