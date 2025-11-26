**bastion server**
A bastion server is a secure intermediary that lets you access protected systems without exposing them directly to the internet.
The bastion is the front door — you go through it first, then tunnel to the private DB

    [Your Laptop] ---> (SSH) ---> [Bastion Host - has public ip] --------------> [Private Server A][Database Server - private IP, no internet access]
    \                                                           Private network connection
                                                                 --------------> [Private Server B][Database Server - private IP, no internet access]