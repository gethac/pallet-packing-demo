import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import brakeDiscUrl from "../../assets/models/brake-disc.glb?url";

let assetPromise = null;
let asset = null;
let assetLoadSettled = false;

export function getBrakeDiscAsset() {
  return asset;
}

export function isBrakeDiscAssetLoadSettled() {
  return assetLoadSettled;
}

/**
 * Load the shared brake-disc geometry once per module. Virtual packages use
 * InstancedMesh, so every preview instance can reuse the same asset.
 */
export function loadBrakeDiscAsset() {
  if (asset) {
    return Promise.resolve(asset);
  }
  if (!assetPromise) {
    assetPromise = new Promise((resolve) => {
      const loader = new GLTFLoader();
      loader.load(
        brakeDiscUrl,
        (gltf) => {
          let sourceMesh = null;
          gltf.scene.traverse((object) => {
            if (!sourceMesh && object.isMesh && object.geometry) {
              sourceMesh = object;
            }
          });
          const sourceMaterial = Array.isArray(sourceMesh?.material)
            ? sourceMesh.material[0]
            : sourceMesh?.material;
          if (!sourceMesh || !sourceMaterial) {
            assetLoadSettled = true;
            resolve(null);
            return;
          }

          const geometry = sourceMesh.geometry.clone();
          geometry.computeBoundingBox();
          const size =
            geometry.boundingBox?.getSize(new THREE.Vector3()) || new THREE.Vector3(1, 1, 1);
          const center =
            geometry.boundingBox?.getCenter(new THREE.Vector3()) || new THREE.Vector3();
          geometry.translate(-center.x, -center.y, -center.z);
          asset = {
            geometry,
            material: sourceMaterial.clone(),
            size,
          };
          assetLoadSettled = true;
          resolve(asset);
        },
        undefined,
        () => {
          assetLoadSettled = true;
          resolve(null);
        }
      );
    });
  }
  return assetPromise;
}
