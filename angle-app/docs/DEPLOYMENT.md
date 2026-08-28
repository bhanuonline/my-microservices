# Deployment

## Build

```bash
./mvnw clean package -DskipTests
```

Produces `target/angle-app-<version>.jar`.

## Run the Jar

```bash
java -jar target/angle-app-<version>.jar \
  --spring.profiles.active=prod
```

Set env vars first (see [ENV-VARIABLES.md](ENV-VARIABLES.md)).

## Docker

### Dockerfile (example)

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/angle-app-*.jar app.jar
EXPOSE 9010
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### Build and Run

```bash
docker build -t angle-app .
docker run -d \
  --name angle-app \
  -p 9010:9010 \
  --env-file .env \
  angle-app
```

## systemd

`/etc/systemd/system/angle-app.service`:

```ini
[Unit]
Description=Angle App
After=network.target

[Service]
User=angle
WorkingDirectory=/opt/angle-app
EnvironmentFile=/etc/angle-app/secrets.env
ExecStart=/usr/bin/java -jar /opt/angle-app/app.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now angle-app
```

## Reverse Proxy (nginx)

```nginx
server {
  listen 443 ssl;
  server_name angle.example.com;

  ssl_certificate     /etc/letsencrypt/live/angle.example.com/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/angle.example.com/privkey.pem;

  location / {
    proxy_pass http://127.0.0.1:9010;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

## Health Check

If Actuator is added:
```bash
curl http://localhost:9010/actuator/health
```

Otherwise:
```bash
curl -f http://localhost:9010/ || exit 1
```

## Rolling Deploy Checklist

- [ ] Build passes locally
- [ ] Tests pass
- [ ] Env vars set on target host
- [ ] New jar copied to `/opt/angle-app/app.jar`
- [ ] `systemctl restart angle-app`
- [ ] Tail logs for 60s
- [ ] Smoke test `/api/analysis/backtest`
