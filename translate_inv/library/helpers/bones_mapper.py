from collections import deque
from typing import Dict

_finger_candidates = ['Thumb', 'Index', 'Middle', 'Ring', 'Pinky', 'Little']

# Key: List of possible handedness representations
_handedness_candidates = {
    'Left': ['Left', 'L_', '_l'],
    'Right': ['Right', 'R_', '_r']
}

# Key: List of possible node names
_handed_bones_candidates = {
    'Eye': ['Eye'],
    'Shoulder': ['Shoulder'],
    'UpArm': ['Arm', 'UpArm', 'UpperArm'],
    'LowArm': ['ForeArm', 'LowArm', 'LowerArm'],
    'Hand': ['Hand'],
    'Thumb': ['Thumb'],
    'Index': ['Index'],
    'Middle': ['Middle'],
    'Ring': ['Ring'],
    'Pinky': ['Pinky', 'Little'],
    'UpLeg': ['UpLeg', 'UpperLeg'],
}

_bones_candidates = dict()

for handedness_key in _handedness_candidates.keys():
    for handed_bones_key in _handed_bones_candidates.keys():
        key = handedness_key + handed_bones_key
        _bones_candidates[key] = []
        for h in _handedness_candidates[handedness_key]:
            for hb in _handed_bones_candidates[handed_bones_key]:
                _bones_candidates[key].append([h, hb])

_bones_candidates['Head'] = [['Head']]
_bones_candidates['Neck'] = [['Neck']]
_bones_candidates['Spine'] = [['Spine']]
_bones_candidates['Hips'] = [['Hip']]


def _is_finger(bone_name: str) -> bool:
    return any((finger.lower() in bone_name.lower() for finger in _finger_candidates))


def _predicate(bone_name: str, p: list[list[str]]) -> bool:
    for strings in p:
        if all((s.lower() in bone_name.lower() for s in strings)):
            return True

    return False


def _first_node_by(
    root: str,
    hierarchy: Dict[str, list[str]],
    key: str,
    result: Dict[str, str]
) -> str | None:
    # BFS
    queue = deque([root])

    while queue:
        current = queue.popleft()
        
        if current not in result.values() and _predicate(current, _bones_candidates[key]):
            return current

        if current in hierarchy:
            for child in hierarchy[current]:
                queue.append(child)
        
    return None


def build_bone_names_map(nodes: dict) -> Dict[str, str]:
    # Build hierarchy
    hierarchy = {
        node['name']: [nodes[index]['name'] for index in node['children']]
            for node in nodes if 'name' in node and 'children' in node
    }

    if not hierarchy:
        raise ValueError('Could not build hierarchy')
        
    # Get root node
    parent_nodes = set(hierarchy.keys())
    child_nodes = set()

    for children in hierarchy.values():
        for child in children:
            child_nodes.add(child)

    root = parent_nodes - child_nodes
    if len(root) > 1:
        root = next((x for x in root if 'root' in x.lower()), None)
    else:
        root = next(iter(root), None)
    if not root:
        raise ValueError('There must be one root node.')

    # Map bones
    result = dict()

    for key in _bones_candidates.keys():
        node = _first_node_by(root, hierarchy, key, result)

        if not node:
            if 'eye' in key.lower():
                continue
            else:
                raise ValueError('Mapping failed: ' + key)
        
        if not _is_finger(node):
            result[key] = node
        else:
            i = 1
            while node in hierarchy:
                result[f'{key}{i}'] = node
                i += 1
                node = _first_node_by(node, hierarchy, key, result)

            result[f'{key}{i}'] = node

    return result