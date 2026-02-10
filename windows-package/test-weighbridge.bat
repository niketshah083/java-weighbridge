@echo off
echo ==========================================
echo   Weighbridge Serial Port Test (Windows)
echo ==========================================
echo.
echo To test weighbridge without real hardware:
echo.
echo 1. Download and install com0com:
echo    https://sourceforge.net/projects/com0com/
echo.
echo 2. Create a virtual port pair (e.g., COM10 ^<-^> COM11)
echo.
echo 3. Update weighbridge config in backend to use: COM10
echo.
echo 4. Run the Java app and select the weighbridge
echo.
echo 5. Use a terminal program (like PuTTY or Tera Term) to send
echo    test data to COM11:
echo    - Open COM11 at 9600 baud
echo    - Type: 1234.56 kg [Enter]
echo.
echo Or use PowerShell to send data:
echo    $port = new-Object System.IO.Ports.SerialPort COM11,9600,None,8,one
echo    $port.Open()
echo    $port.WriteLine("1234.56 kg")
echo    $port.Close()
echo.
echo ==========================================
echo.
echo Alternatively, use DEBUG MODE in the app:
echo - Toggle "Debug Mode" ON in the monitoring screen
echo - Click "Send Test Weight" to simulate weight readings
echo.
pause
