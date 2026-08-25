## look at:
cf-cache-status

Values:
HIT → cached
MISS → fetched from origin
BYPASS → cache disabled
EXPIRED → stale cache

1. curl -I → check headers
2. dig → verify DNS
3. curl origin → isolate issue
4. check error code (520–526)
5. tail logs on server
6. verify cache/WAF behavior
   
🔴 Issue: Old content
Cause: cache HIT
Fix:
Cache purge in Cloudflare

| What you want        | Command                  |
| -------------------- | ------------------------ |
| Your public IP       | `curl ifconfig.me`       |
| Local IP             | `ipconfig getifaddr en0` |
| Website IP           | `dig domain.com`         |
| Origin IP            | From server/cloud        |
| Client IP in backend | `CF-Connecting-IP`       |


Simple Analogy
Private IP = your house number inside society
Public IP = your society’s main address visible to world