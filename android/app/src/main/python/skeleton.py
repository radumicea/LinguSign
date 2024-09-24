import json
import numpy as np
import struct
from io import BufferedReader
from typing import Dict

from helpers.math_types import *
from helpers.bones_mapper import build_bone_names_map
from helpers.three import decompose_mat


_bone_indexes_map = {
  "LeftEye": 2,

  "RightEye": 5,

  "LeftEar": 7,

  "RightEar": 8,

  "LeftShoulder": 11,
  "LeftUpArm": 13,
  "LeftLowArm": 15,

  "RightShoulder": 12,
  "RightUpArm": 14,
  "RightLowArm": 16,

  "LeftHand": 0,

  "LeftThumb1": 1,
  "LeftThumb2": 2,
  "LeftThumb3": 3,
  "LeftThumb4": 4,

  "LeftIndex1": 5,
  "LeftIndex2": 6,
  "LeftIndex3": 7,
  "LeftIndex4": 8,

  "LeftMiddle1": 9,
  "LeftMiddle2": 10,
  "LeftMiddle3": 11,
  "LeftMiddle4": 12,

  "LeftRing1": 13,
  "LeftRing2": 14,
  "LeftRing3": 15,
  "LeftRing4": 16,

  "LeftPinky1": 17,
  "LeftPinky2": 18,
  "LeftPinky3": 19,
  "LeftPinky4": 20,

  "RightHand": 0,

  "RightThumb1": 1,
  "RightThumb2": 2,
  "RightThumb3": 3,
  "RightThumb4": 4,

  "RightIndex1": 5,
  "RightIndex2": 6,
  "RightIndex3": 7,
  "RightIndex4": 8,

  "RightMiddle1": 9,
  "RightMiddle2": 10,
  "RightMiddle3": 11,
  "RightMiddle4": 12,

  "RightRing1": 13,
  "RightRing2": 14,
  "RightRing3": 15,
  "RightRing4": 16,

  "RightPinky1": 17,
  "RightPinky2": 18,
  "RightPinky3": 19,
  "RightPinky4": 20,

  "LeftUpLeg": 23,
  
  "RightUpLeg": 24,
}


_indexes_bone_map = {
    "Head": 0,
    "Neck": 1,
    "Hips": 2,

    "LeftShoulder": 3,
    "LeftUpArm": 4,
    "LeftLowArm": 5,
    "LeftHand": 6,

    "LeftThumb1": 7,
    "LeftThumb2": 8,
    "LeftThumb3": 9,

    "LeftIndex1": 10,
    "LeftIndex2": 11,
    "LeftIndex3": 12,

    "LeftMiddle1": 13,
    "LeftMiddle2": 14,
    "LeftMiddle3": 15,

    "LeftRing1": 16,
    "LeftRing2": 17,
    "LeftRing3": 18,

    "LeftPinky1": 19,
    "LeftPinky2": 20,
    "LeftPinky3": 21,

    "RightShoulder": 22,
    "RightUpArm": 23,
    "RightLowArm": 24,
    "RightHand": 25,

    "RightThumb1": 26,
    "RightThumb2": 27,
    "RightThumb3": 28,

    "RightIndex1": 29,
    "RightIndex2": 30,
    "RightIndex3": 31,

    "RightMiddle1": 32,
    "RightMiddle2": 33,
    "RightMiddle3": 34,

    "RightRing1": 35,
    "RightRing2": 36,
    "RightRing3": 37,

    "RightPinky1": 38,
    "RightPinky2": 39,
    "RightPinky3": 40,
}


class Bone:
    def __init__(self, translation: Vec3, rotation: Vec4, scale: Vec3, name: str) -> None:
        self.translation = translation
        self.rotation = rotation
        self.scale = scale
        self.name = name

class Skeleton:
    def __init__(self, char_path: str) -> None:
        self._read_skeleton_from_file(char_path)


    def get_translation(self, bone_name: str) -> Vec3:
        return self._bones[bone_name].translation
    

    def set_translation(self, bone_name: str, translation: Vec3) -> None:
        self._bones[bone_name].translation = translation
    

    def get_rotation(self, bone_name: str) -> Vec4:
        return self._bones[bone_name].rotation
    

    def set_rotation(self, bone_name: str, rotation: Vec4) -> None:
        self._bones[bone_name].rotation = rotation


    def get_scale(self, bone_name: str) -> Vec3:
        return self._bones[bone_name].scale
    

    def set_scale(self, bone_name: str, scale: Vec3) -> None:
        self._bones[bone_name].scale = scale
    

    def get_mapped_name(self, bone_name: str) -> str:
        return self._bone_names_map[bone_name]


    def get_mapped_index(self, bone_name) -> int:
        return _indexes_bone_map[bone_name]


    def get_landmark(self, bone_name: str, landmarks: VecNx3) -> Vec3:
        return landmarks[_bone_indexes_map[bone_name]]


    def _read_skeleton_from_file(self, char_path: str) -> None:
        with open(char_path, 'rb') as fin:
            self._read_skeleton(fin)
            

    def _read_skeleton(self, char: BufferedReader) -> None:
        def extract_json_from_glb(glb: BufferedReader) -> dict:
            # 12 bytes header
            header = glb.read(12)
            _, version, length = struct.unpack('<Iii', header)
            
            if version != 2:
                raise ValueError("Must be GLB v2")
            
            # Parse chunks
            while glb.tell() < length:
                chunk_header = glb.read(8)
                chunk_length, chunk_type = struct.unpack('<Ii', chunk_header)
                # JSON chunk
                if chunk_type == 0x4E4F534A:
                    json_bytes = glb.read(chunk_length)
                    json_data = json.loads(json_bytes.decode('utf-8'))
                    return json_data
                else:
                    # Skip chunk
                    glb.seek(chunk_length, 1)

            raise ValueError('JSON chunk not found')

        json_data = extract_json_from_glb(char)

        nodes = json_data['nodes']
        
        matrix = {
            node['name']: np.array(node['matrix'], np.float64)
                for node in nodes if 'name' in node and 'matrix' in node
        }

        transforms = {
            node['name']: {
                'translation': np.array(node['translation'], np.float64) if 'translation' in node else np.zeros(3),
                'rotation': np.roll(np.array(node['rotation'], np.float64), shift=1) if 'rotation' in node else np.array([1, 0, 0, 0]),
                'scale': np.array(node['scale'], np.float64) if 'scale' in node else np.ones(3)
                } for node in nodes if 'name' in node
        }

        translation = dict()
        rotation = dict()
        scale = dict()

        if matrix:
            for name, m in matrix.items():
                t, r, s = decompose_mat(m)
                translation[name] = t
                rotation[name] = r
                scale[name] = s
        if transforms:
            for name, transform in transforms.items():
                translation[name] = transform['translation']
                rotation[name] = transform['rotation']
                scale[name] = transform['scale']

        self._bone_names_map = build_bone_names_map(nodes)

        self._bones = dict()
        for key, value in self._bone_names_map.items():
            self._bones[key] = Bone(translation[value], rotation[value], scale[value], value)
