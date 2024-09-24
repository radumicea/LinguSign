import glob
import numpy as np
import os
import re

from .filters.kalman import *
from .filters.moving_average import moving_average_smooth
from .math_types import *


def _process(frames: VecNxNx3, fps: int, start: int, stop: int) -> None:
    dt = 1 / fps
    kalman_filters = [
        kalman_init(frames[start][i], frames[start + 1][i], dt) for i in range(frames.shape[1])
    ]

    for i in range(start + 2, stop):
        frame = frames[i]

        for j, landmark in enumerate(frame):
            if j >= 21:
                break

            A, H, Q, R, P, x = kalman_filters[j]

            if landmark.any():
                # Landmark is detected
                x_pred, P_pred = kalman_predict(A, Q, P, x)
                x_updated, P_updated = kalman_update(H, R, P_pred, x_pred, landmark)
            else:
                # Landmark is missing, use prediction
                x_pred, _ = kalman_predict(A, Q, P, x)
                # For missing landmarks, we only update the position with the prediction
                x_updated = x_pred
                # And set P to a very large number
                P_updated = np.diag([1000000] * 6)

            np.copyto(frames[i][j], x_updated[:3])
            kalman_filters[j] = (A, H, Q, R, P_updated, x_updated)


def _get_non_zero_indices(arr) -> tuple[int, int]:
    first_non_zero = 0
    last_non_zero = len(arr) - 1

    while first_non_zero <= last_non_zero and not arr[first_non_zero].any():
        first_non_zero += 1

    while last_non_zero >= first_non_zero and not arr[last_non_zero].any():
        last_non_zero -= 1

    return (first_non_zero, last_non_zero + 1)


def _check_hands_over_torso_mid(pose, percentage=0.5):
    mid_shoulders = (pose[11][:2] + pose[12][:2]) / 2
    mid_hips = (pose[23][:2] + pose[24][:2]) / 2

    spine_dir = mid_shoulders - mid_hips
    spine_dir /= np.linalg.norm(spine_dir)

    l_mid_torso = pose[11][:2] * percentage + pose[23][:2] * (1 - percentage)
    r_mid_torso = pose[12][:2] * percentage + pose[24][:2] * (1 - percentage)

    left_hand = pose[15][:2]
    right_hand = pose[16][:2]

    l_mid_torso_proj = np.dot(l_mid_torso, spine_dir)
    left_hand_proj = np.dot(left_hand, spine_dir)

    r_mid_torso_proj = np.dot(r_mid_torso, spine_dir)
    right_hand_proj = np.dot(right_hand, spine_dir)

    return left_hand_proj >= l_mid_torso_proj or right_hand_proj >= r_mid_torso_proj


def _filter_useless(arr):
    pose = arr[0]

    first_at_mid_torso = 0
    last_at_mid_torso = arr.shape[1] - 1

    while first_at_mid_torso <= last_at_mid_torso:
        if _check_hands_over_torso_mid(pose[first_at_mid_torso], 0.2):
            break

        first_at_mid_torso += 1

    while last_at_mid_torso >= first_at_mid_torso:
        if _check_hands_over_torso_mid(pose[last_at_mid_torso], 0.2):
            break

        last_at_mid_torso -= 1

    return arr[:,first_at_mid_torso:last_at_mid_torso + 1]


def save_smoothed(
    directory: str,
    word: str,
    window_size: int
) -> int:
    pattern = os.path.join(directory, f'{word}__fps*.npy')
    files = glob.glob(pattern)

    if len(files) != 1:
        raise ValueError()

    file = files[0]
    landmarks_path = os.path.join(directory, file)

    frames = np.load(landmarks_path)
    frames = _filter_useless(frames)

    left_hand = frames[1]
    right_hand = frames[2]

    left_start_stop = _get_non_zero_indices(left_hand)
    right_start_stop = _get_non_zero_indices(right_hand)

    fps = int(re.search(r'__fps(\d+)\.npy', file).group(1))

    _process(left_hand, fps, *left_start_stop)
    _process(right_hand, fps, *right_start_stop)

    start = min(left_start_stop[0], right_start_stop[0])
    stop = max(left_start_stop[1], right_start_stop[1])

    if window_size == 1:
        frames = frames[:,start:stop]
    else:
        frames = [moving_average_smooth(value, window_size) for value in frames[:,start:stop]]

    np.save(os.path.join(directory, f'{word}_smooth.npy'), frames)

    return fps