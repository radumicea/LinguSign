import numpy as np

from helpers.math_types import *
from helpers.three import *
from skeleton import Skeleton


null_lm_quat = np.array([0, 0, 0, 0.5])

_bones_Q = dict()


def compute_R(a: Vec3, b: Vec3) -> Mat4:
    a = normalize(a)
    b = normalize(b)

    dot = a.dot(b)
    cross_len = np.linalg.norm(np.cross(a, b))

    u = a
    v = normalize(b - dot * a)
    w = normalize(np.cross(b, a))

    C = make_basis(u, v, w).transpose()

    R_uvw = new_mat(
        dot,        -cross_len,  0, 0,
        cross_len,   dot,        0, 0,
        0,           0,          1, 0,
        0,           0,          0, 1
    )

    return mult_mat(mult_mat(C.transpose(), R_uvw), C)


def Q_and_R_by_calculating_joints(
    joint_landmark: Vec3,
    joint_child_landmark: Vec3,
    joint_child: Vec3,
    R_chain: Mat4
) -> tuple[Vec4, Mat4]:
    direction = normalize(joint_child_landmark - joint_landmark)
    R = compute_R(
        joint_child,
        apply_matrix_4(direction, R_chain.transpose())
    )

    return Q_from_R(R), mult_mat(R_chain, R)


def calc_hand_R(
    skeleton: Skeleton,
    hand_landmarks: VecNx3,
    handedness: Literal['Left', 'Right'],
    R_low_arm: Mat4
) -> None:
    hand_name = f'{handedness}Hand'
    hand_lm = skeleton.get_landmark(hand_name, hand_landmarks)
    index1_lm = skeleton.get_landmark(f'{handedness}Index1', hand_landmarks)
    middle1_lm = skeleton.get_landmark(f'{handedness}Middle1', hand_landmarks)
    pinky1_lm = skeleton.get_landmark(f'{handedness}Pinky1', hand_landmarks)

    dir_hand_lm = middle1_lm - hand_lm
    dir_index_to_pinky_lm = pinky1_lm - index1_lm

    hand_lm_v = normalize(dir_hand_lm)
    hand_lm_w = np.cross(normalize(dir_index_to_pinky_lm), hand_lm_v)
    hand_lm_u = np.cross(hand_lm_v, hand_lm_w)
    R_hand_lm = make_basis(hand_lm_u, hand_lm_v, hand_lm_w)

    dir_index_to_pinky_bone = normalize(skeleton.get_translation(f'{handedness}Pinky1') - skeleton.get_translation(f'{handedness}Index1'))

    hand_bone_v = normalize(skeleton.get_translation(f'{handedness}Middle1'))
    hand_bone_w = np.cross(dir_index_to_pinky_bone, hand_bone_v)
    hand_bone_u = np.cross(hand_bone_v, hand_bone_w)
    R_hand_bone = make_basis(hand_bone_u, hand_bone_v, hand_bone_w)

    R_bone_to_lm = mult_mat(R_hand_lm, R_hand_bone.transpose())
    R_to_T_pose = R_low_arm.transpose()
    R_hand = mult_mat(R_to_T_pose, R_bone_to_lm)
    Q_hand = Q_from_R(R_hand)
    R_hand = mult_mat(R_low_arm, R_from_Q(Q_hand))
    _bones_Q[hand_name] = Q_hand

    R_chain_thumb = np.copy(R_hand)
    R_chain_index = np.copy(R_hand)
    R_chain_middle = np.copy(R_hand)
    R_chain_ring = np.copy(R_hand)
    R_chain_pinky = np.copy(R_hand)

    R_chains = [R_chain_thumb, R_chain_index, R_chain_middle, R_chain_ring, R_chain_pinky]

    fingers = ['Thumb', 'Index', 'Middle', 'Ring', 'Pinky']

    for i in range(5):
        for j in range(1, 4, 1):
            bone_name = f'{handedness}{fingers[i]}{j}'
            next_bone_name = f'{handedness}{fingers[i]}{j + 1}'

            R_chain = R_chains[i]

            joint_landmark = skeleton.get_landmark(bone_name, hand_landmarks)
            joint_child_landmark = skeleton.get_landmark(next_bone_name, hand_landmarks)

            joint_child = skeleton.get_translation(next_bone_name)

            Q, R = Q_and_R_by_calculating_joints(joint_landmark, joint_child_landmark, joint_child, R_chain)
            R_chains[i] = R
            _bones_Q[bone_name] = Q


def calc_arm_R(
    skeleton: Skeleton,
    pose_landmarks: VecNx3,
    handedness: Literal['Left', 'Right'],
    shoulder_inside: Vec3,
    shoulder_lm: Vec3,
    R_hips: Mat4
) -> Mat4:
    shoulder_name = f'{handedness}Shoulder'
    up_arm_name = f'{handedness}UpArm'
    low_arm_name = f'{handedness}LowArm'
    hand_name = f'{handedness}Hand'

    up_arm_lm = skeleton.get_landmark(up_arm_name, pose_landmarks)

    Q_shoulder, R_shoulder = Q_and_R_by_calculating_joints(
        shoulder_inside,
        shoulder_lm,
        skeleton.get_translation(up_arm_name),
        R_hips
    )
    _bones_Q[shoulder_name] = Q_shoulder

    Q_up_arm, R_up_arm = Q_and_R_by_calculating_joints(
        shoulder_lm,
        up_arm_lm,
        skeleton.get_translation(low_arm_name),
        R_shoulder
    )
    _bones_Q[up_arm_name] = Q_up_arm

    Q_low_arm, R_low_arm = Q_and_R_by_calculating_joints(
        up_arm_lm,
        skeleton.get_landmark(low_arm_name, pose_landmarks),
        skeleton.get_translation(hand_name),
        R_up_arm
    )
    _bones_Q[low_arm_name] = Q_low_arm

    return R_low_arm


def update_landmarks(dist_from_cam: float, offset: Vec3, landmarks: VecNx3) -> None:
    ip_lt = unproject(np.array([-1, 1, -1]))
    ip_rb = unproject(np.array([1, -1, -1]))
    ip_diff = ip_rb - ip_lt
    x_scale = np.abs(ip_diff[0])

    def proj_scale(p_ms: Vec3, cam_pos: Vec3, src_d: float, dst_d: float) -> Vec3:
        vec_cam2p = p_ms - cam_pos
        return cam_pos + vec_cam2p * (dst_d / src_d)

    for lm in landmarks:
        new_lm = unproject(
            np.array([(lm[0] - 0.5) * 2, -(lm[1] - 0.5) * 2, 0])
        )
        new_lm[2] = -lm[2] * x_scale - camera_near + camera_position[2]
        new_lm = proj_scale(new_lm, camera_position, camera_near, dist_from_cam)
        new_lm += offset

        np.copyto(lm, new_lm)


def calc_R(
    skeleton: Skeleton,
    pose: VecNx3,
    left_hand: VecNx3,
    right_hand: VecNx3,
) -> VecNx4:
    pose = np.array(pose, np.float64)
    left_hand = np.array(left_hand, np.float64)
    right_hand = np.array(right_hand, np.float64)

    update_landmarks(1.5, np.array([1, 0, -1.5]), pose)

    left_shoulder_lm = skeleton.get_landmark('LeftShoulder', pose)
    right_shoulder_lm = skeleton.get_landmark('RightShoulder', pose)

    left_up_leg_lm = skeleton.get_landmark('LeftUpLeg', pose)
    right_up_leg_lm = skeleton.get_landmark('RightUpLeg', pose)

    center_shoulders = (left_shoulder_lm + right_shoulder_lm) / 2
    center_hips = (left_up_leg_lm + right_up_leg_lm) / 2
    center_ears = (skeleton.get_landmark('LeftEar', pose) + skeleton.get_landmark('RightEar', pose)) / 2

    spine_vector = center_shoulders - center_hips
    length_spine = np.linalg.norm(spine_vector)
    dir_spine = normalize(spine_vector)
    hips = center_hips + dir_spine * length_spine / 9
    spine = center_hips + dir_spine * length_spine / 9 * 3

    shoulders_vector = right_shoulder_lm - left_shoulder_lm
    neck = center_shoulders + dir_spine * length_spine / 9
    left_shoulder_inside = left_shoulder_lm + shoulders_vector * 1 / 3
    right_shoulder_inside = left_shoulder_lm + shoulders_vector * 2 / 3

    head_vector = center_ears - neck
    head = neck + head_vector * 0.5

    # Hips

    v_hip_to_left = normalize(left_up_leg_lm - hips)
    R_hip_to_left = compute_R(skeleton.get_translation('LeftUpLeg'), v_hip_to_left)
    Q_hip_to_left = Q_from_R(R_hip_to_left)

    v_hip_to_right = normalize(right_up_leg_lm - hips)
    R_hip_to_right = compute_R(skeleton.get_translation('RightUpLeg'), v_hip_to_right)
    Q_hip_to_right = Q_from_R(R_hip_to_right)

    v_hip_to_spine = normalize(spine - hips)
    R_hip_to_spine = compute_R(skeleton.get_translation('Spine'), v_hip_to_spine)
    Q_hip_to_spine = Q_from_R(R_hip_to_spine)

    Q_hips = slerp(Q_hip_to_spine, slerp(Q_hip_to_left, Q_hip_to_right, 0.5), 1 / 3)
    # R_hips = R_from_Q(skeleton.get_rotation('Hips'))
    # skeleton.set_rotation('Hips', Q_hips)
    R_hips = R_from_Q(Q_hips)
    _bones_Q['Hips'] = Q_hips

    # Neck

    Q_neck, R_neck = Q_and_R_by_calculating_joints(neck, head, skeleton.get_translation('Head'), R_hips)
    _bones_Q['Neck'] = Q_neck

    try:
        v_left_eye = normalize(skeleton.get_landmark('LeftEye', pose) - head)
        R_head_to_left_eye = compute_R(
            skeleton.get_translation('LeftEye'),
            apply_matrix_4(v_left_eye, R_neck.transpose())
        )
        Q_head_to_left_eye = Q_from_R(R_head_to_left_eye)

        v_right_eye = normalize(skeleton.get_landmark('RightEye', pose) - head)
        R_head_to_right_eye = compute_R(
            skeleton.get_translation('RightEye'),
            apply_matrix_4(v_right_eye, R_neck.transpose())
        )
        Q_head_to_right_eye = Q_from_R(R_head_to_right_eye)

        Q_head = slerp(Q_head_to_left_eye, Q_head_to_right_eye, 0.5)
        _bones_Q['Head'] = Q_head
    except:
        _bones_Q['Head'] = null_lm_quat

    # Left Shoulder-UpArm-LowArm

    R_left_low_arm = calc_arm_R(
        skeleton,
        pose,
        'Left',
        left_shoulder_inside,
        left_shoulder_lm,
        R_hips
    )

    # Right Shoulder-UpArm-LowArm

    R_right_low_arm = calc_arm_R(
        skeleton,
        pose,
        'Right',
        right_shoulder_inside,
        right_shoulder_lm,
        R_hips
    )

    # Left UpLeg-LowLeg-Foot

    # Right UpLeg-LowLeg-Foot

    update_landmarks(1.5, np.array([1, 0, -1.5]), left_hand)
    calc_hand_R(skeleton, left_hand, 'Left', R_left_low_arm)

    update_landmarks(1.5, np.array([1, 0, -1.5]), right_hand)
    calc_hand_R(skeleton, right_hand, 'Right', R_right_low_arm)

    result = np.repeat([[0, 0, 0, 0.5]], 41, axis=0)

    for key, value in _bones_Q.items():
        result[skeleton.get_mapped_index(key)] = value

    return result


if __name__ == '__main__':

    # test compute_R
    a = np.array([23, 37, 43])
    b = np.array([34, 87, 12])
    result = compute_R(a, b)
    expected = np.array([
        [ 0.96695466,  0.06291538, -0.24706344,  0],
        [-0.19485893,  0.80728901, -0.55705875,  0],
        [ 0.16440403,  0.58679307,  0.79287149,  0],
        [ 0,           0,           0,           1],
    ])
    assert(np.allclose(expected, result))


    # test Q_and_R_by_calculating_joints
    result_q, result_m = Q_and_R_by_calculating_joints(
        np.array([1, 2, 3]),
        np.array([4, 5, 6]),
        np.array([7, 8, 9]),
        new_mat(
            10, 11, 12, 13,
            14, 15, 16, 17,
            18, 19, 29, 21,
            22, 23, 24, 25
        )
    )
    expected_q = np.array([0.9998952514392578, 0.005880723030779638, 0.007277445836535216, -0.011042736434193214])
    expected_m = np.array([
        [9.578329759903609,  13.43020728682359,  17.149935603181003, 21.133962340663555],
        [11.357437837659957, 15.491258602081441, 19.7294747606129,   23.758900130924403],
        [12.011003487395843, 16.020313328908042, 29.02804737623741,  24.038933011932443],
        [13,                 17,                 21,                 25                ]
    ])
    assert(np.allclose(expected_q, result_q) and np.allclose(expected_m, result_m))


    # test update_landmarks
    v = np.array([231.76, 29.389, 31.324])
    update_landmarks(1.5, np.array([1, 0, -1.5]), [v])
    expected = [np.array([766.5626648104053, -70.72556805232574, -53.14930251])]
    assert(np.allclose(expected, v))
    