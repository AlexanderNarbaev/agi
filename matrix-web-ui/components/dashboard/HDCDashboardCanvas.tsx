'use client';

import { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import * as THREE from 'three';

interface HDCDashboardCanvasProps {
  hits: string[];
}

/**
 * WAVE T-05 — HDC dashboard 3D canvas.
 *
 * Shows the memory base as a sparse point cloud. Retrieved memories
 * (in `hits`) are rendered larger and brighter with pulsing rings.
 */
export default function HDCDashboardCanvas({ hits }: HDCDashboardCanvasProps) {
  return (
    <Canvas
      camera={{ position: [0, 0, 30], fov: 60 }}
      dpr={[1, 2]}
      style={{ height: 256 }}
    >
      <ambientLight intensity={0.3} />
      <pointLight position={[10, 10, 10]} intensity={1} color="#10b981" />
      <pointLight position={[-10, -10, -10]} intensity={0.5} color="#a78bfa" />
      <MemoryCloud hits={hits} />
    </Canvas>
  );
}

function MemoryCloud({ hits }: { hits: string[] }) {
  const groupRef = useRef<THREE.Group>(null);
  const { positions, hitPositions } = useMemoryData(hits);

  useFrame((_, delta) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += delta * 0.08;
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
        </bufferGeometry>
        <pointsMaterial
          size={0.2}
          color="#06b6d4"
          transparent
          opacity={0.4}
          sizeAttenuation
        />
      </points>

      {/* Highlighted retrieved memories */}
      {hitPositions.map((pos, i) => (
        <mesh key={i} position={pos}>
          <sphereGeometry args={[0.4, 16, 16]} />
          <meshBasicMaterial color="#10b981" />
        </mesh>
      ))}
    </group>
  );
}

function useMemoryData(hits: string[]) {
  return useMemo(() => {
    // Background memory cloud: 500 random points in [-15, 15]^3
    const positions: number[] = [];
    for (let i = 0; i < 500; i++) {
      positions.push(
        (Math.random() - 0.5) * 30,
        (Math.random() - 0.5) * 30,
        (Math.random() - 0.5) * 30,
      );
    }

    // Highlight positions: deterministic for hits
    const hitPositions: [number, number, number][] = hits.map((_, i) => {
      // Use a hash of the hit id for stability
      let h = i * 2654435761;
      h = (h ^ (h >>> 16)) >>> 0;
      return [
        ((h % 1000) / 1000 - 0.5) * 20,
        (((h >>> 10) % 1000) / 1000 - 0.5) * 20,
        (((h >>> 20) % 1000) / 1000 - 0.5) * 20,
      ];
    });

    return {
      positions: new Float32Array(positions),
      hitPositions,
    };
  }, [hits]);
}
