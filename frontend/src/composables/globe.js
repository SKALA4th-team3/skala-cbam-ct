/* 배경 구 — 목업의 캔버스 렌더러를 그대로 옮겼다.
   화면이 바뀌면 구가 바뀌는 게 아니라 카메라만 움직인다. */
/** 접근성 설정에서 모션을 줄인 사용자는 구를 돌리지 않는다 */
const reduce = typeof matchMedia === 'function'
  && matchMedia('(prefers-reduced-motion: reduce)').matches

export function createGlobe(canvas) {
  const Globe = (function(){
    const cv = canvas; const ctx = cv && cv.getContext('2d'); if (!ctx) return { flyTo(){}, setScroll(){} };
    const N = 5200, SPIN = 0.0014;
    let W = 0, H = 0, dpr = 1, base = 340, raf = 0, t = 0, scroll = 0;

    /* camera state, eased toward target every frame */
    const cam = { yaw: 0, tilt: -0.28, zoom: 1, cy: 0.5, alpha: 1, drift: 1 };
    const tgt = Object.assign({}, cam);

    const pts = []; const GA = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < N; i++) { const y = 1 - (i / (N - 1)) * 2, r = Math.sqrt(Math.max(0, 1 - y * y)), th = GA * i; pts.push([Math.cos(th) * r, y, Math.sin(th) * r]); }
    const PH = (1 + Math.sqrt(5)) / 2;
    const V = [[-1,PH,0],[1,PH,0],[-1,-PH,0],[1,-PH,0],[0,-1,PH],[0,1,PH],[0,-1,-PH],[0,1,-PH],[PH,0,-1],[PH,0,1],[-PH,0,-1],[-PH,0,1]].map(v => { const l = Math.hypot(v[0],v[1],v[2]); return [v[0]/l,v[1]/l,v[2]/l]; });
    let F = [[0,11,5],[0,5,1],[0,1,7],[0,7,10],[0,10,11],[1,5,9],[5,11,4],[11,10,2],[10,7,6],[7,1,8],[3,9,4],[3,4,2],[3,2,6],[3,6,8],[3,8,9],[4,9,5],[2,4,11],[6,2,10],[8,6,7],[9,8,1]];
    const cache = new Map();
    const mid = (a, b) => { const k = a < b ? a + '_' + b : b + '_' + a; if (cache.has(k)) return cache.get(k); const p = V[a], q = V[b]; const m = [p[0]+q[0],p[1]+q[1],p[2]+q[2]]; const l = Math.hypot(m[0],m[1],m[2]); V.push([m[0]/l,m[1]/l,m[2]/l]); cache.set(k, V.length - 1); return V.length - 1; };
    const sub = f => f.flatMap(([a,b,c]) => { const ab = mid(a,b), bc = mid(b,c), ca = mid(c,a); return [[a,ab,ca],[b,bc,ab],[c,ca,bc],[ab,bc,ca]]; });
    F = sub(sub(F));
    const eset = new Set(), EDGES = [];
    for (const f of F) for (const [x, y] of [[f[0],f[1]],[f[1],f[2]],[f[2],f[0]]]) { const k = x < y ? x + '_' + y : y + '_' + x; if (!eset.has(k)) { eset.add(k); EDGES.push([x, y]); } }

    const D2R = Math.PI / 180;

    function resize(){ dpr = Math.min(2, devicePixelRatio || 1); W = innerWidth; H = innerHeight; cv.width = W * dpr; cv.height = H * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); base = Math.min(W * 0.215, H * 0.35); }

    function frame(){
      t += 1;
      /* ease camera */
      tgt.yaw += SPIN * tgt.drift;
      for (const k of ['tilt','zoom','cy','alpha']) cam[k] += (tgt[k] - cam[k]) * 0.055;
      cam.yaw += (tgt.yaw - cam.yaw) * 0.055;

      /* camera distance follows the sphere, and closes in a little as we zoom — never past the surface */
      const R = base * cam.zoom, D = R * Math.max(1.75, 3.4 - (cam.zoom - 1) * 0.55);
      const CX = W / 2, CY = H * cam.cy;
      const yawS = cam.yaw;
      const cyv = Math.cos(yawS), syv = Math.sin(yawS), ct = Math.cos(cam.tilt), st = Math.sin(cam.tilt);
      const proj = p => { const x1 = p[0] * cyv + p[2] * syv, z1 = -p[0] * syv + p[2] * cyv; const y2 = p[1] * ct - z1 * st, z2 = p[1] * st + z1 * ct; const k = D / (D + z2 * R); return [CX - x1 * R * k, CY - y2 * R * k, z2, k]; };

      ctx.clearRect(0, 0, W, H); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = cam.alpha;

      const sizeMul = 1 + (cam.zoom - 1) * 0.42;
      const pv = V.map(proj); ctx.lineWidth = 1;
      for (const e of EDGES) {
        const A = pv[e[0]], B = pv[e[1]];
        if ((A[0] < -60 && B[0] < -60) || (A[0] > W + 60 && B[0] > W + 60) || (A[1] < -60 && B[1] < -60) || (A[1] > H + 60 && B[1] > H + 60)) continue;
        ctx.strokeStyle = 'rgba(255,255,255,' + ((A[2] + B[2]) / 2 < 0 ? 0.085 : 0.026) + ')';
        ctx.beginPath(); ctx.moveTo(A[0], A[1]); ctx.lineTo(B[0], B[1]); ctx.stroke();
      }
      for (let i = 0; i < N; i++) {
        const P = pts[i], q = proj(P);
        if (q[0] < -20 || q[0] > W + 20 || q[1] < -20 || q[1] > H + 20) continue;
        const z = q[2], k = q[3], front = z < 0;
        const wave = Math.sin(P[1] * 3.0 - t * 0.014 + P[0] * 1.1), boost = wave > 0.9 ? (wave - 0.9) / 0.1 : 0;
        const a = Math.min(1, (front ? 0.26 + (1 - Math.abs(z)) * 0.30 : 0.07) + boost * (front ? 0.45 : 0.12));
        ctx.fillStyle = 'rgba(255,255,255,' + a.toFixed(3) + ')';
        ctx.beginPath(); ctx.arc(q[0], q[1], ((front ? 0.72 : 0.5) * k + boost * 0.8) * sizeMul, 0, 6.283); ctx.fill();
      }

      ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
      if (!reduce) raf = requestAnimationFrame(frame);
    }

    resize(); addEventListener('resize', () => { resize(); if (reduce) frame(); }); frame();
    document.addEventListener('visibilitychange', () => { if (document.hidden) cancelAnimationFrame(raf); else if (!reduce) { cancelAnimationFrame(raf); raf = requestAnimationFrame(frame); } });

    return {
      /* fly the camera so (lat, lon) faces the viewer, then dolly in */
      flyTo(o){
        if (o.lat != null) {
          /* tilt = -lat and yaw = PI - lon puts (lat, lon) dead centre, facing the camera */
          tgt.tilt = -o.lat * D2R;
          const want = Math.PI - o.lon * D2R, TAU = Math.PI * 2;
          const d = ((want - cam.yaw + Math.PI) % TAU + TAU) % TAU - Math.PI;
          tgt.yaw = cam.yaw + d;
        }
        if (o.zoom != null) tgt.zoom = o.zoom;
        if (o.cy != null) tgt.cy = o.cy;
        if (o.alpha != null) tgt.alpha = o.alpha;
        tgt.drift = o.drift != null ? o.drift : 0;
      },
      setScroll(v){ scroll = v; }
    };
  })();
  return Globe
}
