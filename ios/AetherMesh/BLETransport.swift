import AetherMeshKit
import CoreBluetooth
import Foundation

struct DiscoveredNode: Identifiable, Equatable {
    let id: UUID
    let name: String
    let rssi: Int
}

/// CoreBluetooth central for one AetherMesh node. Delivers notified frames on
/// `queue`; the app forwards them to MeshSession on the same serial queue.
final class BLETransport: NSObject {
    enum State: Equatable {
        case poweredOff
        case unauthorized
        case idle
        case scanning
        case connecting(UUID)
        case ready(UUID)
    }

    var onStateChange: ((State) -> Void)?
    var onDiscovered: ((DiscoveredNode) -> Void)?
    var onFrame: ((Data) -> Void)?

    private let queue: DispatchQueue
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var txCharacteristic: CBCharacteristic?
    private(set) var state: State = .idle {
        didSet { if state != oldValue { onStateChange?(state) } }
    }

    private let serviceUUID = CBUUID(string: AetherMeshBLE.serviceUUID)
    private let txUUID = CBUUID(string: AetherMeshBLE.txCharacteristicUUID)
    private let rxUUID = CBUUID(string: AetherMeshBLE.rxCharacteristicUUID)

    init(queue: DispatchQueue) {
        self.queue = queue
        super.init()
        central = CBCentralManager(delegate: self, queue: queue)
    }

    func startScan() {
        guard central.state == .poweredOn else { return }
        central.scanForPeripherals(withServices: [serviceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        state = .scanning
    }

    func stopScan() {
        central.stopScan()
        if state == .scanning { state = .idle }
    }

    func connect(_ id: UUID) {
        guard let target = central.retrievePeripherals(withIdentifiers: [id]).first else { return }
        central.stopScan()
        peripheral = target
        target.delegate = self
        state = .connecting(id)
        central.connect(target)
    }

    func disconnect() {
        if let peripheral { central.cancelPeripheralConnection(peripheral) }
    }

    /// Writes one protobuf frame. Refuses frames larger than the negotiated ATT
    /// payload instead of letting CoreBluetooth truncate them.
    func write(_ frame: Data) -> Bool {
        guard let peripheral, let txCharacteristic, case .ready = state else { return false }
        guard frame.count <= peripheral.maximumWriteValueLength(for: .withResponse) else { return false }
        peripheral.writeValue(frame, for: txCharacteristic, type: .withResponse)
        return true
    }
}

extension BLETransport: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn: state = .idle
        case .unauthorized: state = .unauthorized
        default: state = .poweredOff
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name ?? "AetherMesh"
        onDiscovered?(DiscoveredNode(id: peripheral.identifier, name: name, rssi: RSSI.intValue))
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        reset()
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        reset()
    }

    private func reset() {
        peripheral = nil
        txCharacteristic = nil
        state = central.state == .poweredOn ? .idle : .poweredOff
    }
}

extension BLETransport: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == serviceUUID }) else {
            central.cancelPeripheralConnection(peripheral)
            return
        }
        peripheral.discoverCharacteristics([txUUID, rxUUID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let characteristics = service.characteristics ?? []
        txCharacteristic = characteristics.first { $0.uuid == txUUID }
        guard txCharacteristic != nil, let rx = characteristics.first(where: { $0.uuid == rxUUID }) else {
            central.cancelPeripheralConnection(peripheral)
            return
        }
        peripheral.setNotifyValue(true, for: rx)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        // Firmware sends its auth challenge ~400 ms after connect; being ready only
        // once notifications are on means that challenge is not lost.
        if characteristic.uuid == rxUUID, characteristic.isNotifying, error == nil {
            state = .ready(peripheral.identifier)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == rxUUID, error == nil, let value = characteristic.value else { return }
        onFrame?(value)
    }
}
