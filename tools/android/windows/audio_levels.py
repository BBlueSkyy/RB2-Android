# Per-5-second RMS (dBFS) of FL FR C LFE BL BR in guest audio dumps (6 planar BE float32 x 256 per frame).
import numpy as np,sys
for fn in sys.argv[1:]:
    d=np.fromfile(fn,dtype='>f4'); fr=d.size//1536; d=d[:fr*1536].reshape(fr,6,256).astype(np.float64)
    per=int(48000/256)*5
    print(fn,'seconds',fr*256/48000)
    for s in range(0,fr//per):
        r=np.sqrt((d[s*per:(s+1)*per]**2).mean(axis=(0,2)))
        print(f' {s*5:4d}s '+' '.join(f'{20*np.log10(x+1e-9):6.1f}' for x in r))
