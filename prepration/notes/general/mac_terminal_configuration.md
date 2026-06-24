#Get all the java


cat ~/.zshrc

# Show all listening ports
sudo lsof -nP -iTCP -sTCP:LISTEN

# Count listening ports
sudo lsof -nP -iTCP -sTCP:LISTEN | wc -l

# Check what is using port 8080
sudo lsof -i :8080

# Show only port numbers
sudo lsof -nP -iTCP -sTCP:LISTEN | awk '{print $9}'

# Show all Java processes
jps -lv

# Show all running Java processes
ps -ef | grep java

# Find process using port 8080
lsof -i :8080

# Kill process (replace PID)
kill -9 <PID>

# Check if Kafka is responding
nc -zv localhost 9092

# Example
kill -9 12345

sudo → Run as administrator.
lsof → List Open Files. In Unix, network connections are treated as files.
-n → Don't resolve IP addresses to hostnames (faster).
-P → Don't resolve port numbers to service names (shows 8080 instead of http-alt).
-iTCP → Show TCP network connections only.
-sTCP:LISTEN → Show only ports that are listening for incoming connections.
| → Pipe output from one command to another.
wc → Word Count.
-l → Count lines.
-i :8080 → Show processes using port 8080.
awk → Text processing tool.
{print $9} → Print the 9th column (contains IP:PORT).
jps → Java Process Status.
-l → Show full class/JAR name.
-v → Show JVM arguments.
ps → Process status.
-e → All processes.
-f → Full format.
grep java → Filter only Java processes.

# check firewall
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --listapps
sudo pfctl -sr