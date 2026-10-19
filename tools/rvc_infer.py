"""Runs Applio's conversion (core.py infer …) reproducibly: same input → same result, on every machine and on CPU or GPU.

  python rvc_infer.py <Applio folder> infer --input-path … --output-path … (all arguments like core.py)

RVC mixes in noise while generating (torch.randn_like in the synthesizer), without a fixed seed – the same singing then sounds minimally
different on every run, and a GPU rolls different dice than the CPU. Here
- the randomness is seeded (Python, NumPy, Torch) and
- torch.randn_like/torch.randn are replaced so the noise is always made on the CPU with its own fixed generator and only then moves to the
  device. So CPU and GPU get exactly the same random numbers; only rounding differences of the hardware remain.
VOICEAPI_DEVICE=cpu forces the CPU (even if a GPU is present).
"""
from __future__ import annotations

import os
import random
import runpy
import sys

SEED = 1234


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Usage: rvc_infer.py <Applio folder> <core.py arguments>")
    applio = os.path.abspath(sys.argv[1])
    if os.environ.get("VOICEAPI_DEVICE", "").lower() == "cpu":
        os.environ["CUDA_VISIBLE_DEVICES"] = ""
        os.environ["HIP_VISIBLE_DEVICES"] = ""
    import numpy as np
    import torch
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    generator = torch.Generator(device="cpu")
    generator.manual_seed(SEED)
    original_randn = torch.randn

    def randn_like(tensor, *args, **kwargs):
        dtype = kwargs.get("dtype") or tensor.dtype
        noise = original_randn(tuple(tensor.shape), generator=generator, dtype=torch.float32)
        return noise.to(device=tensor.device, dtype=dtype)

    def randn(*size, **kwargs):
        device = kwargs.pop("device", None)
        dtype = kwargs.pop("dtype", None)
        kwargs.pop("generator", None)
        shape = tuple(size[0]) if len(size) == 1 and isinstance(size[0], (tuple, list, torch.Size)) else tuple(size)
        noise = original_randn(shape, generator=generator, dtype=torch.float32, **kwargs)
        return noise.to(device=device or "cpu", dtype=dtype or torch.float32)

    torch.randn_like = randn_like
    torch.randn = randn
    os.chdir(applio)
    sys.path.insert(0, applio)
    sys.argv = [os.path.join(applio, "core.py")] + sys.argv[2:]
    runpy.run_path(sys.argv[0], run_name="__main__")


if __name__ == "__main__":
    main()
