import numpy as np

from ..math_types import *


def _moving_average_filter(data: Vec, window_size: int) -> Vec:
    # Extend the start and end of the data array to handle boundary conditions
    pad_size = window_size // 2
    padded_data = np.pad(data, (pad_size, pad_size), 'edge')
    # Apply the moving average filter
    smoothed = np.convolve(padded_data, np.ones(window_size) / window_size, mode='same')
    # Return the section of the smoothed array that corresponds to the original data size
    return smoothed[pad_size:-pad_size]

def _transform(a: VecNxNx3) -> VecNxNx3:
    return np.transpose(a, (1, 0, 2))

def moving_average_smooth(
    a: VecNxNx3,
    window_size: int
) -> VecNxNx3:
    a = _transform(a)

    smoothed = np.zeros_like(a)
    for i in range(a.shape[0]):
        for j in range(3):
            smoothed[i, :, j] = _moving_average_filter(a[i, :, j], window_size)

    return _transform(smoothed)