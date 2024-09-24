import numpy as np

from ..math_types import Vec3

_dim_state = 6  # 3 For position (x, y, z) and 3 for velocity (vx, vy, vz)
_dim_measurement = 3  # Only position (x, y, z) is measured

# x - State variable
# A - State transition matrix
# H - State-to-measurement matrix
# P - State covariance matrix
# Q - Process noise covariance matrix
# R - Measurement noise covariance matrix


def kalman_init(
    first_landmark: Vec3,
    second_landmark: Vec3,
    dt: float,
    process_noise_cov: float = 0.001,
    measurement_noise_cov: float = 0.0015
):
    """Initialize and return Kalman Filter parameters for a 3D landmark."""

    # Initial state
    v = (np.array(second_landmark) - np.array(first_landmark)) / dt
    x = np.concatenate((first_landmark, v))
    # x = [x, y, z, vx, vy, vz]

    # Assume constant velocity:
    # x[t+1] = Ax[t] + u
    # u - Process noise

    A = np.array([
        [1,   0,   0,   dt,  0,   0 ],
        [0,   1,   0,   0,   dt,  0 ],
        [0,   0,   1,   0,   0,   dt],
        [0,   0,   0,   1,   0,   0 ],
        [0,   0,   0,   0,   1,   0 ],
        [0,   0,   0,   0,   0,   1 ]
    ])


    # measurement = Hx + v
    # measurement: [x, y, z] (no velocities!)
    # => H extracts positions from from state vector

    H = np.array([
        [1,  0,  0,  0,  0,  0],
        [0,  1,  0,  0,  0,  0],
        [0,  0,  1,  0,  0,  0]
    ])

    # First 3 elements of the main diagonal are measurement_noise_cov.
    # The last 3 are a very large value because
    # it represents a large uncertainty for the velocity state values.
    P = np.diag([measurement_noise_cov] * 3 + [1000000] * 3)

    Q = np.eye(_dim_state) * process_noise_cov
    R = np.eye(_dim_measurement) * measurement_noise_cov

    return A, H, Q, R, P, x

def kalman_predict(A, Q, P, x):
    """Predict the next state."""

    x_pred = A @ x
    # x[t+1] = 1 * x[t] + dt * xv[t]
    # y[t+1] = 1 * y[t] + dt * yv[t]
    # z[t+1] = 1 * z[t] + dt * zv[t]
    # vx[t+1] = vx[t], vy[t+1] = vy[t], vz[t+1] = vz[t]

    P_pred = A @ P @ A.T + Q

    return x_pred, P_pred

def kalman_update(H, R, P_pred, x_pred, measurement):
    """Update the state with a new measurement."""
    # Measurement residual
    y = measurement - H @ x_pred
    # Residual covariance
    S = H @ P_pred @ H.T + R
    # Kalman gain
    K = P_pred @ H.T @ np.linalg.inv(S)
    x_updated = x_pred + K @ y
    P_updated = (np.eye(_dim_state) - K @ H) @ P_pred
    return x_updated, P_updated