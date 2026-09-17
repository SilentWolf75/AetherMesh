"""Generate ios/AetherMeshKit Swift protobuf types from proto/mesh.proto.

protoc parses mesh.proto into a descriptor set; this script wraps it in a
CodeGeneratorRequest and pipes it to protoc-gen-swift directly. That lets a
Windows protoc.exe drive a protoc-gen-swift built in WSL/Linux, which protoc's
own --plugin flag cannot do.

  python proto/generate_swift_proto.py --protoc PATH --plugin-cmd "CMD ..."

Example (Windows host, plugin built inside WSL):
  python proto/generate_swift_proto.py \
      --protoc firmware/.pio/protoc/bin/protoc.exe \
      --plugin-cmd "wsl -d Ubuntu-26.04 -- /home/me/protoc-gen-swift-build/release/protoc-gen-swift"

Build the plugin with `swift build -c release --product protoc-gen-swift` in a
swift-protobuf checkout matching Package.resolved.
"""

from __future__ import annotations

import argparse
import os
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path

from google.protobuf import descriptor_pb2
from google.protobuf.compiler import plugin_pb2

ROOT = Path(__file__).resolve().parents[1]
PROTO_DIR = ROOT / "proto"
OUTPUT_DIR = ROOT / "ios" / "AetherMeshKit" / "Sources" / "AetherMeshKit" / "Proto"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--protoc", required=True, help="protoc executable")
    parser.add_argument("--plugin-cmd", required=True, help="command that runs protoc-gen-swift")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        descriptor_path = Path(tmp) / "mesh.desc"
        subprocess.run(
            [args.protoc, f"-I{PROTO_DIR}", "--include_imports", "--include_source_info",
             f"--descriptor_set_out={descriptor_path}", "mesh.proto"],
            check=True,
        )
        descriptors = descriptor_pb2.FileDescriptorSet.FromString(descriptor_path.read_bytes())

    request = plugin_pb2.CodeGeneratorRequest()
    request.file_to_generate.append("mesh.proto")
    request.parameter = "Visibility=Public"
    request.proto_file.extend(descriptors.file)

    # POSIX splitting would eat the backslashes in a Windows path.
    plugin_argv = shlex.split(args.plugin_cmd, posix=os.name != "nt")
    result = subprocess.run(
        plugin_argv, input=request.SerializeToString(), capture_output=True, check=False
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr.decode(errors="replace"))
        return result.returncode
    response = plugin_pb2.CodeGeneratorResponse.FromString(result.stdout)
    if response.error:
        sys.stderr.write(response.error + "\n")
        return 1

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for generated in response.file:
        target = OUTPUT_DIR / Path(generated.name).name
        target.write_text(generated.content, encoding="utf-8", newline="\n")
        print(f"wrote {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
