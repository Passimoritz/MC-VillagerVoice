"""Startet Applios Umwandlung (core.py infer …) reproduzierbar: gleiche Eingabe → gleiches Ergebnis, auf jedem Rechner und auf CPU wie GPU.

  python rvc_infer.py <Applio-Ordner> infer --input-path … --output-path … (alle Argumente wie core.py)

RVC mischt beim Erzeugen Rauschen ein (torch.randn_like im Synthesizer), ohne festen Startwert – derselbe Gesang klingt dann bei jedem Lauf minimal
anders, und eine GPU wuerfelt anders als die CPU. Hier wird
- der Zufall fest gesaet (Python, NumPy, Torch) und
- torch.randn_like/torch.randn so ersetzt, dass das Rauschen immer auf der CPU mit einem eigenen, festen Generator entsteht und erst danach aufs
  Geraet wandert. So bekommen CPU und GPU exakt dieselben Zufallszahlen; uebrig bleiben nur Rundungsunterschiede der Rechenwerke.
VOICEAPI_DEVICE=cpu erzwingt die CPU (auch wenn eine GPU da ist).
"""
from __future__ import annotations

import os
import random
import runpy
import sys

SEED = 1234


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Aufruf: rvc_infer.py <Applio-Ordner> <core.py-Argumente>")
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
