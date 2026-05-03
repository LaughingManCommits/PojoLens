from __future__ import annotations

import asyncio
import time
from typing import Any


class RateLimitBucket:
    """Sliding-window async rate limiter for TPM and RPM limits."""

    def __init__(
        self,
        tpm_limit: int | None = None,
        rpm_limit: int | None = None,
        window_sec: float = 60.0,
    ) -> None:
        self._tpm = tpm_limit
        self._rpm = rpm_limit
        self._window = window_sec
        self._token_events: list[tuple[float, int]] = []
        self._request_events: list[float] = []
        self._lock = asyncio.Lock()
        self._throttle_count = 0
        self._total_delay_ms = 0

    @property
    def enabled(self) -> bool:
        return self._tpm is not None or self._rpm is not None

    async def acquire(self, estimated_tokens: int) -> float:
        """Block until window permits dispatch. Returns total seconds delayed."""
        if not self.enabled:
            return 0.0
        total_delay = 0.0
        while True:
            sleep_for = await self._try_reserve(estimated_tokens, total_delay)
            if sleep_for == 0.0:
                return total_delay
            sleep_for = max(sleep_for, 0.05)
            await asyncio.sleep(sleep_for)
            total_delay += sleep_for

    async def _try_reserve(self, estimated_tokens: int, total_delay: float) -> float:
        async with self._lock:
            now = time.monotonic()
            self._prune(now)
            sleep_for = self._compute_wait(now, estimated_tokens)
            if sleep_for <= 0.0:
                self._token_events.append((now, max(estimated_tokens, 0)))
                self._request_events.append(now)
                if total_delay > 0.0:
                    self._throttle_count += 1
                    self._total_delay_ms += int(total_delay * 1000)
                return 0.0
            return sleep_for

    def _prune(self, now: float) -> None:
        cutoff = now - self._window
        self._token_events = [(t, tok) for (t, tok) in self._token_events if t > cutoff]
        self._request_events = [t for t in self._request_events if t > cutoff]

    def _compute_wait(self, now: float, estimated_tokens: int) -> float:
        tpm_wait = 0.0
        if self._tpm is not None and estimated_tokens > 0:
            current = sum(tok for (_, tok) in self._token_events)
            if current + estimated_tokens > self._tpm:
                if not self._token_events:
                    # Window empty but estimate still exceeds limit; allow through.
                    tpm_wait = 0.0
                else:
                    tokens_to_free = current + estimated_tokens - self._tpm
                    cumulative = 0
                    for ts, tok in sorted(self._token_events):
                        cumulative += tok
                        if cumulative >= tokens_to_free:
                            tpm_wait = max(0.0, (ts + self._window) - now)
                            break
                    else:
                        tpm_wait = self._window
        rpm_wait = 0.0
        if self._rpm is not None and len(self._request_events) >= self._rpm:
            oldest = min(self._request_events)
            rpm_wait = max(0.0, (oldest + self._window) - now)
        return max(tpm_wait, rpm_wait)

    async def record_completion(self, actual_tokens: int, estimated_tokens: int = 0) -> None:
        """Charge the delta between actual and estimated tokens into the window."""
        delta = actual_tokens - estimated_tokens
        if delta > 0:
            async with self._lock:
                self._token_events.append((time.monotonic(), delta))

    def stats(self) -> dict[str, Any]:
        return {
            "tpmLimit": self._tpm,
            "rpmLimit": self._rpm,
            "windowSec": self._window,
            "throttleCount": self._throttle_count,
            "totalDelayMs": self._total_delay_ms,
        }
