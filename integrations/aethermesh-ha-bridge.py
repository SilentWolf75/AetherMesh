#!/usr/bin/env python3
import sys
import os
import time
import json
import argparse
import serial
import serial.tools.list_ports
import paho.mqtt.client as mqtt

# Default Configuration
DEFAULT_MQTT_HOST = "127.0.0.1"
DEFAULT_MQTT_PORT = 1883
DEFAULT_MQTT_USER = ""
DEFAULT_MQTT_PASS = ""
DEFAULT_BAUDRATE = 115200

def auto_detect_serial_port():
    ports = list(serial.tools.list_ports.comports())
    # Exclude standard internal virtual ports
    candidates = [p.device for p in ports if "Standard" not in p.description]
    if candidates:
        print(f"Auto-detected serial port: {candidates[0]}")
        return candidates[0]
    return None

def register_ha_device(client, node_id, model, fw_version):
    """
    Registers a new node in Home Assistant using MQTT Auto-Discovery.
    """
    device_id = f"aethermesh_{node_id}"
    device_info = {
        "identifiers": [device_id],
        "name": f"AetherMesh Node 0x{node_id:04X}",
        "model": model if model else "Generic Node",
        "sw_version": fw_version if fw_version else "1.0.0",
        "manufacturer": "AetherMesh"
    }

    # 1. Device Tracker (GPS Location)
    tracker_config_topic = f"homeassistant/device_tracker/{device_id}/config"
    tracker_payload = {
        "name": f"Location",
        "unique_id": f"{device_id}_tracker",
        "state_topic": f"aethermesh/node_{node_id}/location",
        "source_type": "gps",
        "device": device_info
    }
    client.publish(tracker_config_topic, json.dumps(tracker_payload), retain=True)

    # 2. Battery Sensor
    battery_config_topic = f"homeassistant/sensor/{device_id}_battery/config"
    battery_payload = {
        "name": "Battery",
        "unique_id": f"{device_id}_battery",
        "state_topic": f"aethermesh/node_{node_id}/battery",
        "unit_of_measurement": "%",
        "device_class": "battery",
        "state_class": "measurement",
        "device": device_info
    }
    client.publish(battery_config_topic, json.dumps(battery_payload), retain=True)

    # 3. RSSI Sensor
    rssi_config_topic = f"homeassistant/sensor/{device_id}_rssi/config"
    rssi_payload = {
        "name": "RSSI",
        "unique_id": f"{device_id}_rssi",
        "state_topic": f"aethermesh/node_{node_id}/rssi",
        "unit_of_measurement": "dBm",
        "device_class": "signal_strength",
        "state_class": "measurement",
        "device": device_info
    }
    client.publish(rssi_config_topic, json.dumps(rssi_payload), retain=True)

    # 4. SNR Sensor
    snr_config_topic = f"homeassistant/sensor/{device_id}_snr/config"
    snr_payload = {
        "name": "SNR",
        "unique_id": f"{device_id}_snr",
        "state_topic": f"aethermesh/node_{node_id}/snr",
        "unit_of_measurement": "dB",
        "state_class": "measurement",
        "device": device_info
    }
    client.publish(snr_config_topic, json.dumps(snr_payload), retain=True)

    # 5. Uptime Sensor
    uptime_config_topic = f"homeassistant/sensor/{device_id}_uptime/config"
    uptime_payload = {
        "name": "Uptime",
        "unique_id": f"{device_id}_uptime",
        "state_topic": f"aethermesh/node_{node_id}/uptime",
        "unit_of_measurement": "s",
        "state_class": "total_increasing",
        "device": device_info
    }
    client.publish(uptime_config_topic, json.dumps(uptime_payload), retain=True)

    print(f"Registered device 0x{node_id:04X} inside Home Assistant.")

def publish_telemetry(client, data):
    node_id = data["node_id"]
    
    # Register/Update HA discovery topics
    register_ha_device(client, node_id, data.get("model"), data.get("fw"))

    # Publish states
    client.publish(f"aethermesh/node_{node_id}/battery", str(data["battery"]), retain=True)
    client.publish(f"aethermesh/node_{node_id}/rssi", str(data["rssi"]), retain=True)
    client.publish(f"aethermesh/node_{node_id}/snr", str(data["snr"]), retain=True)
    client.publish(f"aethermesh/node_{node_id}/uptime", str(data["uptime"]), retain=True)

    # Location payload: HA device_tracker expects latitude & longitude keys
    loc_payload = {
        "latitude": data["lat"],
        "longitude": data["lon"],
        "gps_accuracy": 15
    }
    client.publish(f"aethermesh/node_{node_id}/location", json.dumps(loc_payload), retain=True)

    print(f"Telemetry published for node 0x{node_id:04X} (Battery: {data['battery']}%, RSSI: {data['rssi']} dBm)")

def main():
    parser = argparse.ArgumentParser(description="AetherMesh Home Assistant USB Serial Bridge")
    parser.add_argument("--port", help="USB Serial Port (e.g. COM3 or /dev/ttyUSB0)")
    parser.add_argument("--baud", type=int, default=DEFAULT_BAUDRATE, help="Baudrate (default 115200)")
    parser.add_argument("--host", default=DEFAULT_MQTT_HOST, help="MQTT Broker Host")
    parser.add_argument("--mqtt-port", type=int, default=DEFAULT_MQTT_PORT, help="MQTT Broker Port")
    parser.add_argument("--user", default=DEFAULT_MQTT_USER, help="MQTT User")
    parser.add_argument("--password", default=DEFAULT_MQTT_PASS, help="MQTT Password")
    args = parser.parse_args()

    port = args.port if args.port else auto_detect_serial_port()
    if not port:
        print("Error: No serial port specified and none auto-detected.")
        sys.exit(1)

    # Setup MQTT Client
    print(f"Connecting to MQTT Broker {args.host}:{args.mqtt_port}...")
    client = mqtt.Client()
    if args.user:
        client.username_pw_set(args.user, args.password)
    
    try:
        client.connect(args.host, args.mqtt_port, 60)
        client.loop_start()
    except Exception as e:
        print(f"Failed to connect to MQTT broker: {e}")
        sys.exit(1)

    print(f"Listening on serial port: {port} at {args.baud} baud...")
    try:
        ser = serial.Serial(port, args.baud, timeout=1.0)
    except Exception as e:
        print(f"Failed to open serial port: {e}")
        sys.exit(1)

    # Read serial line loop
    try:
        while True:
            if ser.in_waiting > 0:
                line = ser.readline().decode('utf-8', errors='ignore').strip()
                if line.startswith("{"):
                    try:
                        data = json.loads(line)
                        if data.get("event") == "telemetry":
                            publish_telemetry(client, data)
                    except json.JSONDecodeError:
                        pass # Ignore malformed json
                    except KeyError as e:
                        print(f"Key error parsing line: {e}")
            time.sleep(0.05)
    except KeyboardInterrupt:
        print("\nExiting bridge...")
    finally:
        ser.close()
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()
