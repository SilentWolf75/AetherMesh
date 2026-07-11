"""Deterministic AetherMesh routing benchmark used by CI and field tuning."""

from __future__ import annotations

import argparse
import heapq
import json
import random
from dataclasses import asdict, dataclass
from typing import Dict, Iterable, List, Optional, Tuple


def hop_cost(snr: float) -> int:
    bounded = max(-20.0, min(10.0, snr))
    return int(1.0 + (10.0 - bounded) * (24.0 / 30.0))


@dataclass(frozen=True)
class Link:
    snr: float
    success_rate: float
    latency_ms: int = 250


@dataclass
class DeliveryMetrics:
    attempted: int = 0
    delivered: int = 0
    transmissions: int = 0
    retries: int = 0
    latency_ms: int = 0
    route_changes: int = 0

    @property
    def delivery_rate(self) -> float:
        return self.delivered / self.attempted if self.attempted else 0.0

    @property
    def average_latency_ms(self) -> float:
        return self.latency_ms / self.delivered if self.delivered else 0.0

    def to_dict(self) -> dict:
        result = asdict(self)
        result["delivery_rate"] = round(self.delivery_rate, 4)
        result["average_latency_ms"] = round(self.average_latency_ms, 2)
        return result


class MeshScenario:
    def __init__(self, seed: int = 1) -> None:
        self._links: Dict[str, Dict[str, Link]] = {}
        self._offline: set[str] = set()
        self._rng = random.Random(seed)

    def connect(self, left: str, right: str, *, snr: float, success_rate: float,
                latency_ms: int = 250) -> "MeshScenario":
        if not 0.0 <= success_rate <= 1.0:
            raise ValueError("success_rate must be between 0 and 1")
        link = Link(snr, success_rate, latency_ms)
        self._links.setdefault(left, {})[right] = link
        self._links.setdefault(right, {})[left] = link
        return self

    def set_online(self, node: str, online: bool) -> None:
        if online:
            self._offline.discard(node)
        else:
            self._offline.add(node)

    def route(self, source: str, target: str) -> Optional[List[str]]:
        if source in self._offline or target in self._offline:
            return None
        queue: List[Tuple[int, str, List[str]]] = [(0, source, [source])]
        best = {source: 0}
        while queue:
            metric, node, path = heapq.heappop(queue)
            if node == target:
                return path
            if metric != best.get(node):
                continue
            for neighbor, link in sorted(self._links.get(node, {}).items()):
                if neighbor in self._offline:
                    continue
                candidate = metric + hop_cost(link.snr)
                if candidate < best.get(neighbor, 1 << 30):
                    best[neighbor] = candidate
                    heapq.heappush(queue, (candidate, neighbor, path + [neighbor]))
        return None

    def benchmark(self, source: str, target: str, messages: int,
                  max_retries: int = 3, retry_delay_ms: int = 3000) -> DeliveryMetrics:
        metrics = DeliveryMetrics(attempted=messages)
        previous_route: Optional[List[str]] = None
        for _ in range(messages):
            delivered = False
            message_latency = 0
            for attempt in range(max_retries + 1):
                path = self.route(source, target)
                if path is None:
                    if attempt < max_retries:
                        metrics.retries += 1
                        message_latency += retry_delay_ms
                    continue
                if previous_route is not None and path != previous_route:
                    metrics.route_changes += 1
                previous_route = path
                attempt_latency = 0
                for left, right in zip(path, path[1:]):
                    link = self._links[left][right]
                    metrics.transmissions += 1
                    attempt_latency += link.latency_ms
                    if self._rng.random() > link.success_rate:
                        break
                else:
                    metrics.delivered += 1
                    metrics.latency_ms += message_latency + attempt_latency
                    delivered = True
                    break
                message_latency += attempt_latency
                if attempt < max_retries:
                    metrics.retries += 1
                    message_latency += retry_delay_ms
            if not delivered:
                previous_route = None
        return metrics


def standard_scenarios(seed: int) -> Iterable[Tuple[str, MeshScenario, str, str]]:
    direct = MeshScenario(seed).connect("A", "B", snr=8, success_rate=0.98)
    yield "direct_strong", direct, "A", "B"

    five_hop = MeshScenario(seed)
    for index in range(5):
        five_hop.connect(str(index), str(index + 1), snr=-4, success_rate=0.93)
    yield "five_hop", five_hop, "0", "5"

    alternate = MeshScenario(seed)
    alternate.connect("A", "B", snr=7, success_rate=0.98)
    alternate.connect("B", "D", snr=7, success_rate=0.98)
    alternate.connect("A", "C", snr=-2, success_rate=0.95)
    alternate.connect("C", "D", snr=-2, success_rate=0.95)
    alternate.set_online("B", False)
    yield "relay_failure_fallback", alternate, "A", "D"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20260711)
    parser.add_argument("--messages", type=int, default=1000)
    args = parser.parse_args()
    report = {
        name: scenario.benchmark(source, target, args.messages).to_dict()
        for name, scenario, source, target in standard_scenarios(args.seed)
    }
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
