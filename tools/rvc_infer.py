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

import json
import os
import random
import runpy
import sys

SEED = 1234


def patch_f0(options: dict) -> None:
    """VOICEAPI_F0 (JSON, the arguments of villager_sing.shape_f0): Applio's pitch curve is shaped before the model sings it – gaps
    bridged, smoothed, every phrase moved by octaves into the villager's register. The pitch key (--pitch) is applied afterwards."""
    import numpy as np
    from rvc.infer import pipeline
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from villager_sing import shape_f0

    original = pipeline.Pipeline.get_f0

    def get_f0(self, x, p_len, f0_method="rmvpe", pitch=0, f0_autotune=False, f0_autotune_strength=1.0, proposed_pitch=False,
               proposed_pitch_threshold=155.0):
        _, f0 = original(self, x, p_len, f0_method, 0, False, 1.0, False, proposed_pitch_threshold)
        f0 = shape_f0(f0, frame_s=self.window / self.sample_rate, **options) * 2 ** (pitch / 12)
        f0_mel = 1127 * np.log(1 + f0 / 700)
        f0_mel[f0_mel > 0] = (f0_mel[f0_mel > 0] - self.f0_mel_min) * 254 / (self.f0_mel_max - self.f0_mel_min) + 1
        f0_mel[f0_mel <= 1] = 1
        f0_mel[f0_mel > 255] = 255
        return np.rint(f0_mel).astype(int), f0

    pipeline.Pipeline.get_f0 = get_f0


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

    # How much randomness the model may add (1 = RVC's own amount):
    # - VOICEAPI_PRIOR_NOISE scales the sampling of the latent voice (z_p = m_p + exp(logs_p)·noise·0.667, 192 channels) – less = cleaner,
    #   steadier voice, more = breathier;
    # - VOICEAPI_SOURCE_NOISE scales the noise of the excitation source (the "breath" mixed into the sine waves of the vocoder).
    prior_scale = float(os.environ.get("VOICEAPI_PRIOR_NOISE", "1.0"))
    source_scale = float(os.environ.get("VOICEAPI_SOURCE_NOISE", "1.0"))

    def randn_like(tensor, *args, **kwargs):
        dtype = kwargs.get("dtype") or tensor.dtype
        noise = original_randn(tuple(tensor.shape), generator=generator, dtype=torch.float32)
        if tensor.dim() == 3 and tensor.shape[1] == 192:
            noise = noise * prior_scale
        elif tensor.dim() == 3 and tensor.shape[2] <= 16:
            noise = noise * source_scale
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
    shape = os.environ.get("VOICEAPI_F0", "")
    if shape:
        patch_f0(json.loads(shape))
    sys.argv = [os.path.join(applio, "core.py")] + sys.argv[2:]
    runpy.run_path(sys.argv[0], run_name="__main__")


if __name__ == "__main__":
    main()
