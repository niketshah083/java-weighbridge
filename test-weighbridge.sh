#!/bin/bash

echo "=========================================="
echo "  Weighbridge Serial Port Test"
echo "=========================================="
echo ""

# Check if socat is installed
if ! command -v socat &> /dev/null; then
    echo "Installing socat..."
    sudo apt install -y socat
fi

echo "Creating virtual serial port pair..."
echo "  /tmp/ttyV0 - Use this in weighbridge config"
echo "  /tmp/ttyV1 - Simulator will send data here"
echo ""

# Create virtual serial ports in background
socat -d -d pty,raw,echo=0,link=/tmp/ttyV0 pty,raw,echo=0,link=/tmp/ttyV1 &
SOCAT_PID=$!

sleep 2

echo "Virtual ports created!"
echo ""
echo "Now you can:"
echo "1. Update weighbridge config in backend to use: /tmp/ttyV0"
echo "2. Run the Java app and select the weighbridge"
echo "3. In another terminal, send test data:"
echo ""
echo "   # Send single weight:"
echo "   echo '1234.56 kg' > /tmp/ttyV1"
echo ""
echo "   # Send continuous weights (every 2 seconds):"
echo "   while true; do echo \"\$(shuf -i 1000-2000 -n 1).$(shuf -i 10-99 -n 1) kg\" > /tmp/ttyV1; sleep 2; done"
echo ""
echo "Press Ctrl+C to stop virtual ports"

# Wait for user to stop
trap "kill $SOCAT_PID 2>/dev/null; echo 'Virtual ports stopped.'" EXIT
wait $SOCAT_PID
