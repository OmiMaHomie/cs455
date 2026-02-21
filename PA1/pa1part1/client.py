import sys
import socket

def main():
    # Check cmd-line args
    if len(sys.argv) != 3:
        print("Using: python3 client.py <hostname> <port>")
        sys.exit(1)
    
    hostname = sys.argv[1]
    port = int(sys.argv[2])
    
    # Create TCP socket
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    # Waits 5 sec for send/recieve. If no response by then, timeout.
    client_socket.settimeout(5.0)

    
    try:
        # Connect to server
        client_socket.connect((hostname, port))
        print(f"Connected to {hostname}:{port}")
        
        # Send msg
        msg = "Yoyo"
        client_socket.sendall(msg.encode())
        print(f"Sent: {msg}")
        
        # Check for response
        response = client_socket.recv(1024)
        print(f"Received: {response.decode().strip()}")
        
    except socket.timeout:
        print("Error: Connection timed out")
        sys.exit(1)
    except ConnectionRefusedError:
        print("Error: Connection refused")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
    finally:
        client_socket.close()

if __name__ == "__main__":
    main()  