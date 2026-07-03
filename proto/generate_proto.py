import os
import sys
import urllib.request
import zipfile
import subprocess
import shutil

def main():
    proto_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(proto_dir)
    
    protoc_temp = os.path.join(project_root, "firmware", ".pio", "protoc")
    zip_path = os.path.join(proto_dir, "protoc.zip")
    
    # 1. Download protoc if not present
    protoc_exe = os.path.join(protoc_temp, "bin", "protoc.exe")
    if not os.path.exists(protoc_exe):
        print("Downloading protoc...")
        url = "https://github.com/protocolbuffers/protobuf/releases/download/v25.1/protoc-25.1-win64.zip"
        urllib.request.urlretrieve(url, zip_path)
        
        print("Extracting protoc...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(protoc_temp)
            
        os.remove(zip_path)
        print("protoc downloaded and extracted successfully.")
    else:
        print("protoc already downloaded.")
        
    # 2. Paths to files
    proto_file = os.path.join(proto_dir, "mesh.proto")
    firmware_src = os.path.join(project_root, "firmware", "src")
    
    os.makedirs(firmware_src, exist_ok=True)
    
    # 3. Locate nanopb_generator
    # Look in standard paths or AppData
    appdata = os.environ.get("APPDATA")
    nanopb_gen = os.path.join(appdata, "Python", "Python313", "Scripts", "nanopb_generator.exe")
    if not os.path.exists(nanopb_gen):
        # Fallback to checking path
        nanopb_gen = "nanopb_generator.exe"
        
    print(f"Using nanopb generator: {nanopb_gen}")
    
    # Add protoc to system PATH so nanopb_generator can find it
    protoc_bin_dir = os.path.join(protoc_temp, "bin")
    os.environ["PATH"] = protoc_bin_dir + os.pathsep + os.environ.get("PATH", "")
    
    # 4. Execute generator
    cmd = [
        nanopb_gen,
        "-I", proto_dir,
        "-D", firmware_src,
        proto_file
    ]
    
    print(f"Running command: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode == 0:
        print("Successfully generated nanopb C++ files in firmware/src:")
        print(f"  - {os.path.join(firmware_src, 'mesh.pb.h')}")
        print(f"  - {os.path.join(firmware_src, 'mesh.pb.c')}")
    else:
        print("Error running nanopb generator:")
        print(result.stdout)
        print(result.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
