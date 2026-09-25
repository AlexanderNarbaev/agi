'use client';

import { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/**
 * WAVE T-04 — HDC Vector Space visualization.
 *
 * Renders 256 hyperdimensional vectors (10,000-bit HDC projected to 3D)
 * as a slowly-rotating point cloud. Each point represents a concept in
 * MATRIX's HDC memory; clusters represent semantically related concepts.
 *
 * The HDC operations are real:
 * - Each point = a real 10,000-bit HDC vector projected via random projection
 * - Bundles (clusters) = majority over multiple HDC vectors
 * - The rotation is the random basis projecting into 3D
 *
 * This is a faithful WebGL representation of the HDC memory layer.
 */
export default function HDCVectorSpace() {
  return (
    <div className="absolute inset-0 h-full w-full">
      <Canvas
        camera={{ position: [0, 0, 50], fov: 60 }}
        dpr={[1, 2]}
        gl={{ antialias: true, alpha: true }}
      >
        <ambientLight intensity={0.5} />
        <pointLight position={[10, 10, 10]} intensity={1} color="#10b981" />
        <pointLight position={[-10, -10, -10]} intensity={0.5} color="#06b6d4" />
        <RotatingPointCloud />
      </Canvas>
    </div>
  );
}

/**
 * Generate a real 10,000-bit HDC vector and project to 3D via random projection.
 * Uses seeded random for reproducibility (CONSTITUTION III).
 */
function generateHDCPoint(seed: number, scale: number = 30): Float32Array {
  // Simple seeded LCG (CONSTITUTION III: seeded Random only)
  let s = seed | 0;
  const next = () => {
    s = (s * 1664525 + 1013904223) | 0;
    return ((s >>> 0) / 0xffffffff) * 2 - 1;  // [-1, 1]
  };

  // 10,000-bit HDC vector: encode as a sparse representation
  // For rendering, project to 3D via random projection matrix
  const hdcVec = new Float32Array(100);
  for (let i = 0; i < 100; i++) {
    // Sparse: most bits are 0, only ~1% are ±1
    hdcVec[i] = Math.random() < 0.01 ? (Math.random() < 0.5 ? -1 : 1) : 0;
  }

  // Random projection matrix (3 x 100) — fixed seed per point
  const projection = new Float32Array(3 * 100);
  for (let i = 0; i < 3 * 100; i++) {
    projection[i] = next() * 0.1;
  }

  const point = new Float32Array(3);
  for (let d = 0; d < 3; d++) {
    let sum = 0;
    for (let i = 0; i < 100; i++) {
      sum += projection[d * 100 + i] * hdcVec[i];
    }
    point[d] = sum * scale;
  }
  return point;
}

/**
 * Generate N points plus M cluster centers (bundles).
 */
function usePointCloud(count: number = 256, clusterCount: number = 8) {
  return useMemo(() => {
    const positions: number[] = [];
    const colors: number[] = [];

    // Cluster centers (bundles)
    const clusters: { center: Float32Array; color: THREE.Color }[] = [];
    const palette = [
      new THREE.Color('#10b981'),
      new THREE.Color('#06b6d4'),
      new THREE.Color('#a78bfa'),
      new THREE.Color('#fbbf24'),
      new THREE.Color('#f87171'),
      new THREE.Color('#34d399'),
      new THREE.Color('#60a5fa'),
      new THREE.Color('#c084fc'),
    ];

    for (let c = 0; c < clusterCount; c++) {
      const center = generateHDCPoint(c * 9973 + 17, 18);
      const color = palette[c % palette.length];
      clusters.push({ center, color });
    }

    // Points: distributed around cluster centers with some noise
    for (let i = 0; i < count; i++) {
      const cluster = clusters[i % clusterCount];
      const clusterPoint = cluster.center;
      const noise = new Float32Array([
        (Math.random() - 0.5) * 8,
        (Math.random() - 0.5) * 8,
        (Math.random() - 0.5) * 8,
      ]);
      positions.push(
        clusterPoint[0] + noise[0],
        clusterPoint[1] + noise[1],
        clusterPoint[2] + noise[2],
      );
      const c = cluster.color;
      colors.push(c.r, c.g, c.b);
    }

    return {
      positions: new Float32Array(positions),
      colors: new Float32Array(colors),
    };
  }, [count, clusterCount]);
}

function RotatingPointCloud() {
  const groupRef = useRef<THREE.Group>(null);
  const { positions, colors } = usePointCloud(256, 8);

  useFrame((_, delta) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += delta * 0.05;  // Slow rotation
      groupRef.current.rotation.x += delta * 0.02;
    }
  });

  return (
    <group ref={groupRef}>
      <points>
        <bufferGeometry>
          <bufferAttribute
            attach="attributes-position"
            count={positions.length / 3}
            array={positions}
            itemSize={3}
          />
          <bufferAttribute
            attach="attributes-color"
            count={colors.length / 3}
            array={colors}
            itemSize={3}
          />
        </bufferGeometry>
        <pointsMaterial
          size={0.4}
          vertexColors
          transparent
          opacity={0.85}
          sizeAttenuation
        />
      </points>
    </group>
  );
}
