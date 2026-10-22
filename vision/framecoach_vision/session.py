"""Small app integration contract: scene generations reject delayed old inferences."""
from threading import Lock


class SceneSession:
    def __init__(self):
        self._lock = Lock()
        self._generation = 0
        self._request = 0
        self._active = None
        self._result = None

    def invalidate(self):
        with self._lock:
            self._generation += 1
            self._active = None
            self._result = None

    def begin(self):
        with self._lock:
            if self._active is not None:
                return None
            self._request += 1
            self._active = (self._generation, self._request)
            return self._active

    def finish(self, token, result):
        with self._lock:
            if token is None or token != self._active:
                return False
            self._active = None
            self._result = result
            return True

    @property
    def result(self):
        with self._lock:
            return self._result
