import sys
import socket

def main():
    # Check cmd-line args
    if len(sys.argv) != 2:
        print("Using: python3 server.py <port>")
        sys.exit(1)
    
    port = int(sys.argv[1])
    
    # Make sure on right port
    if not (58000 <= port <= 58999):
        print("Warning: Not within port range 58000-58999 for assignment")
    
    # Create TCP socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    # Bind to all interfaces on the specified port
    server_socket.bind(('', port))
    server_socket.listen(1)
    print(f"Server listening on port {port}...")
    
    while True:
        # Accept client connection
        client_conn, client_addr = server_socket.accept()
        print(f"Connected w/ {client_addr}")
        
        try:
            # Receive data (max should be 1024 bytes)
            data = client_conn.recv(1024)
            if data:
                msg = data.decode().strip()
                print(f"Received: {msg}")
                # Echo back same msg
                client_conn.sendall(data)
                print(f"Sent: {msg}")
        except Exception as e:
            print(f"Error: {e}")
        finally:
            client_conn.close()
            print("Connection closed")

if __name__ == "__main__":
    main()