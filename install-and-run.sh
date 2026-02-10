#!/bin/bash

echo "=========================================="
echo "Java Bridge App - Installation Script"
echo "=========================================="
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Maven is not installed. Installing..."
    sudo apt update
    sudo apt install -y maven
    echo "Maven installed successfully!"
else
    echo "Maven is already installed: $(mvn -version | head -n 1)"
fi

echo ""
echo "=========================================="
echo "Building Java Bridge App"
echo "=========================================="
echo ""

# Navigate to project directory
cd "$(dirname "$0")"

# Clean and compile
echo "Compiling project..."
mvn clean compile

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "Build successful! Starting application..."
    echo "=========================================="
    echo ""
    
    # Run the application
    mvn javafx:run
else
    echo ""
    echo "=========================================="
    echo "Build failed! Please check the errors above."
    echo "=========================================="
    exit 1
fi
