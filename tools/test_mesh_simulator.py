import unittest

from tools.mesh_simulator import MeshScenario, hop_cost


class MeshSimulatorTest(unittest.TestCase):
    def test_route_prefers_stronger_path(self):
        mesh = MeshScenario(1)
        mesh.connect("A", "B", snr=8, success_rate=1)
        mesh.connect("B", "D", snr=8, success_rate=1)
        mesh.connect("A", "C", snr=-12, success_rate=1)
        mesh.connect("C", "D", snr=-12, success_rate=1)
        self.assertEqual(["A", "B", "D"], mesh.route("A", "D"))

    def test_route_uses_alternate_when_relay_disappears(self):
        mesh = MeshScenario(2)
        mesh.connect("A", "B", snr=8, success_rate=1)
        mesh.connect("B", "D", snr=8, success_rate=1)
        mesh.connect("A", "C", snr=0, success_rate=1)
        mesh.connect("C", "D", snr=0, success_rate=1)
        mesh.set_online("B", False)
        self.assertEqual(["A", "C", "D"], mesh.route("A", "D"))

    def test_benchmark_is_deterministic(self):
        def run():
            mesh = MeshScenario(77).connect("A", "B", snr=-4, success_rate=0.72)
            return mesh.benchmark("A", "B", 100).to_dict()
        self.assertEqual(run(), run())

    def test_retries_improve_delivery_on_lossy_link(self):
        without = MeshScenario(99).connect("A", "B", snr=-8, success_rate=0.55)
        with_retries = MeshScenario(99).connect("A", "B", snr=-8, success_rate=0.55)
        first = without.benchmark("A", "B", 500, max_retries=0)
        second = with_retries.benchmark("A", "B", 500, max_retries=3)
        self.assertGreater(second.delivery_rate, first.delivery_rate)
        self.assertGreater(second.transmissions, first.transmissions)

    def test_failed_messages_do_not_pollute_delivery_latency(self):
        mesh = MeshScenario(5).connect("A", "B", snr=0, success_rate=0, latency_ms=250)
        result = mesh.benchmark("A", "B", 10, max_retries=2)
        self.assertEqual(0, result.delivered)
        self.assertEqual(0, result.average_latency_ms)

    def test_hop_cost_bounds(self):
        self.assertEqual(1, hop_cost(20))
        self.assertEqual(25, hop_cost(-40))


if __name__ == "__main__":
    unittest.main()
