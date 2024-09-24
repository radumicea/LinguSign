from .math_types import *


def new_mat(*args: float) -> Mat4:
    m = np.array(args, np.float64)
    dim = np.sqrt(m.size)
    dim_i = int(dim)

    if dim != dim_i:
        raise ValueError('Must be a square matrix')

    return m.reshape(dim_i, dim_i).transpose()


projection_matrix_inverse = np.array([
    [0.5522847498307933, 0,                    0,  0                  ],
    [0,                  0.41421356237309503,  0,  0                  ],
    [0,                  0,                    0, -0.49949999999999994],
    [0,                  0,                   -1,  0.5005             ]
])
matrix_world = np.array([
    [1, 0, 0,    0],
    [0, 1, 0,    0],
    [0, 0, 1,    0],
    [0, 1, 1.75, 1]
])
camera_near = 1
camera_position = np.array([0, 1, 1.75])


def unproject(v: Vec3) -> Vec3:
    v = apply_matrix_4(v, projection_matrix_inverse)
    v = apply_matrix_4(v, matrix_world)
    return v


def mult_mat(a: Mat4, b: Mat4) -> Mat4:
    return (a.transpose() @ b.transpose()).transpose()


def normalize(v: NDArray[np.float64]) -> NDArray[np.float64]:
    len_v = np.linalg.norm(v)

    if len_v == 0:
        return np.zeros(v.shape)
    
    return v / len_v


def apply_matrix_4(v: Vec3, m: Mat4) -> Vec3:
    v_homogeneous = np.array([v[0], v[1], v[2], 1])
    
    result = np.dot(m.transpose(), v_homogeneous)
    
    return result[:3] / result[3]


def make_basis(x_axis: Vec3, y_axis: Vec3, z_axis: Vec3) -> Mat4:
    return np.array([
        [x_axis[0], x_axis[1], x_axis[2], 0],
        [y_axis[0], y_axis[1], y_axis[2], 0],
        [z_axis[0], z_axis[1], z_axis[2], 0],
        [0,         0,         0,         1]
    ])


def Q_from_R(m: Mat4) -> Vec4:
    # Actually just flatten
    m = m.reshape(-1)
    m11, m12, m13 = m[0], m[4], m[8]
    m21, m22, m23 = m[1], m[5], m[9]
    m31, m32, m33 = m[2], m[6], m[10]
    
    trace = m11 + m22 + m33
    
    if trace > 0:
        s = 0.5 / np.sqrt(trace + 1)
        w = 0.25 / s
        x = (m32 - m23) * s
        y = (m13 - m31) * s
        z = (m21 - m12) * s
    elif m11 > m22 and m11 > m33:
        s = 2 * np.sqrt(1 + m11 - m22 - m33)
        w = (m32 - m23) / s
        x = 0.25 * s
        y = (m12 + m21) / s
        z = (m13 + m31) / s
    elif m22 > m33:
        s = 2 * np.sqrt(1 + m22 - m11 - m33)
        w = (m13 - m31) / s
        x = (m12 + m21) / s
        y = 0.25 * s
        z = (m23 + m32) / s
    else:
        s = 2 * np.sqrt(1 + m33 - m11 - m22)
        w = (m21 - m12) / s
        x = (m13 + m31) / s
        y = (m23 + m32) / s
        z = 0.25 * s
    
    return np.array([w, x, y, z])


def slerp(qa: Vec4, qb: Vec4, t: float) -> Vec4:
    if t == 0:
        return qa
    if t == 1:
        return qb

    cos_half_theta = np.dot(qa, qb)

    if cos_half_theta < 0:
        qb = -qb
        cos_half_theta = -cos_half_theta

    if cos_half_theta >= 1:
        return qa

    sqr_sin_half_theta = 1 - cos_half_theta * cos_half_theta
    if sqr_sin_half_theta <= np.finfo(float).eps:
        s = 1 - t
        return normalize(s * qa + t * qb)

    sin_half_theta = np.sqrt(sqr_sin_half_theta)
    half_theta = np.arctan2(sin_half_theta, cos_half_theta)
    ratio_a = np.sin((1 - t) * half_theta) / sin_half_theta
    ratio_b = np.sin(t * half_theta) / sin_half_theta

    return ratio_a * qa + ratio_b * qb


def compose_mat(pos: Vec3, Q: Vec4, scale: Vec3 = np.ones(3)) -> Mat4:
    w, x, y, z = Q
    x2, y2, z2 = x + x,	 y + y,  z + z
    xx, xy, xz = x * x2, x * y2, x * z2
    yy, yz, zz = y * y2, y * z2, z * z2
    wx, wy, wz = w * x2, w * y2, w * z2

    sx, sy, sz = scale

    return np.array([
        (1 - (yy + zz)) * sx, (xy + wz) * sx,       (xz - wy) * sx,       0,
        (xy - wz) * sy,       (1 - (xx + zz)) * sy, (yz + wx) * sy,       0,
        (xz + wy) * sz,       (yz - wx) * sz,       (1 - (xx + yy)) * sz, 0,
        pos[0],               pos[1],               pos[2],               1
    ]).reshape((4, 4))


def decompose_mat(m: Mat4) -> tuple[Vec3, Vec4, Vec3]:
    m = m.reshape((4, 4))

    sx = np.linalg.norm(m[0, :3])
    sy = np.linalg.norm(m[1, :3])
    sz = np.linalg.norm(m[2, :3])

    if np.linalg.det(m) < 0:
        sx = -sx

    translation = m[3, :3]

    scale = np.array([sx, sy, sz])

    r = np.zeros((4, 4))
    r[:3, :3] = m[:3, :3] / scale[:, np.newaxis]

    quaternion = Q_from_R(r)

    return (translation, quaternion, scale)


def extract_R(m: Mat4) -> Mat4:
        m = m.reshape(-1)

        scaleX = 1 / np.linalg.norm(np.array([m[0], m[1], m[2]]))
        scaleY = 1 / np.linalg.norm(np.array([m[4], m[5], m[6]]))
        scaleZ = 1 / np.linalg.norm(np.array([m[8], m[9], m[10]]))
          
        return np.array([
            [m[0] * scaleX, m[1] * scaleX, m[ 2] * scaleX, 0],
            [m[4] * scaleY, m[5] * scaleY, m[ 6] * scaleY, 0],
            [m[8] * scaleZ, m[9] * scaleZ, m[10] * scaleZ, 0],
            [0,             0,             0,              1],
        ])


def R_from_Q(quat: Vec4) -> Mat4:
    return compose_mat(np.zeros(3), quat)


if __name__ == '__main__':

    # test apply_matrix_4
    v = np.array([1, 2, 3])
    m = new_mat(
        4,  5,  6,  7 ,
        8,  9,  10, 11,
        12, 13, 14, 15,
        16, 17, 18, 19
    )
    result = apply_matrix_4(v, m)
    expected = np.array([0.3170731707317074, 0.5447154471544716, 0.7723577235772359])
    assert(np.allclose(expected, result))


    # test make_basis
    u = np.array([1, 2, 3])
    v = np.array([4, 5, 6])
    w = np.array([7, 8, 9])
    result = make_basis(u, v, w)
    expected = np.array([
        [1, 2, 3, 0],
        [4, 5, 6, 0],
        [7, 8, 9, 0],
        [0, 0, 0, 1]
    ])
    assert((expected == result).all())


    # test Q_from_R
    identity = np.eye(4)

    rotation_x_90 = new_mat(
        1, 0,                  0,               0,
        0, np.cos(np.pi / 2), -np.sin(np.pi / 2), 0,
        0, np.sin(np.pi / 2),  np.cos(np.pi / 2), 0,
        0, 0,                  0,               1
    )

    rotation_y_90 = new_mat(
         np.cos(np.pi / 2), 0, np.sin(np.pi / 2), 0,
         0,                 1, 0,                 0,
        -np.sin(np.pi / 2), 0, np.cos(np.pi / 2), 0,
         0,                 0, 0,                 1
    )

    rotation_z_90 = new_mat(
        np.cos(np.pi / 2), -np.sin(np.pi / 2), 0, 0,
        np.sin(np.pi / 2),  np.cos(np.pi / 2), 0, 0,
        0,                  0,                 1, 0,
        0,                  0,                 0, 1
    )

    combined_rotation_90_x_then_y = mult_mat(rotation_x_90, rotation_y_90)
    
    rotation_x_180 = new_mat(
        1, 0,              0,             0,
        0, np.cos(np.pi), -np.sin(np.pi), 0,
        0, np.sin(np.pi),  np.cos(np.pi), 0,
        0, 0,              0,             1
    )

    test_matrices = [identity, rotation_x_90, rotation_y_90, rotation_z_90, combined_rotation_90_x_then_y, rotation_x_180]

    expected = [
        np.array([ 1, 0, 0, 0 ]),
        np.array([ 0.7071067811865476, 0.7071067811865475, 0, 0 ]),
        np.array([ 0.7071067811865476, 0, 0.7071067811865475, 0 ]),
        np.array([ 0.7071067811865476, 0, 0, 0.7071067811865475 ]),
        np.array([ 0.5, 0.5, 0.5, 0.5 ]),
        np.array([ 6.123233995736766e-17, 1, 0, 0 ])
    ]

    for t, e in zip(test_matrices, expected):
        assert((e == Q_from_R(t)).all())

    
    # test slerp
    qa = np.array([1, 2, 3, 4])
    qb = np.array([5, 6, 7, 8])
    assert((slerp(qa, qb, 0) == qa).all())
    assert((slerp(qa, qb, 1) == qb).all())

    qa = np.array([1, 0, 0, 0])
    qb = np.array([-1, 0, 0, 0])
    assert((slerp(qa, qb, 0.5) == np.array([1, 0, 0, 0])).all())

    qa = np.array([1, 0, 0, 0])
    qb = np.array([1, 0, 0, 0])
    assert((slerp(qa, qb, 0.5) == qa).all())

    qa = np.array([0.7071068, 0, 0, 0.7071068])
    qb = np.array([0, 0, 0.7071068, 0.7071068])
    assert((slerp(qa, qb, 0.5) == np.array([0.40824829770516413, 0, 0.40824829770516413, 0.8164965954103283])).all())

    qa = np.array([1, 0, 0, 0])
    qb = np.array([1 - np.finfo(float).eps, 0, np.finfo(float).eps, 0])
    assert((slerp(qa, qb, 0.5) == np.array([0.9999999999999999, 0, 1.1102230246251565e-16, 0])).all())

    qa = np.array([0.7071068, 0.7071068, 0, 0])
    qb = np.array([0.7071068, 0.7071068 + np.finfo(float).eps, np.finfo(float).eps, 0])
    assert((slerp(qa, qb, 0.5) == np.array([0.7071068, 0.7071068, 0, 0])).all())


    # test compose_mat
    pos = np.array([0, 103.99147034, 2.07609391])
    q = np.array([0.99561772, 0.09101666,  0.00978048, -0.01912292])
    result = compose_mat(pos, q)
    expected = np.array([
        [0.9990773122642652,  -0.036297862774000135, -0.022956247011468537, 0],
        [0.03985860925165068,  0.9827005627154562,    0.1808615362846678,   0],
        [0.01599422977498581, -0.18160966163283088,   0.983240619286812,    0],
        [0,                    103.99147034,          2.07609391,           1]
    ])
    assert(np.allclose(expected, result))


    # test decompose_mat
    m = new_mat(1,2,3,4, 5,6,7,8, 9,10,11,12, 13,14,15,16)
    p, q, s = decompose_mat(m)
    expected_p = np.array([4, 8, 12])
    expected_q = np.array([0.7787722394527179, 0.10335168406503917, -0.20732387455457288, 0.10090799904412008])
    expected_s = np.array([10.344080432788601, 11.832159566199232, 13.379088160259652])
    assert(np.allclose(expected_p, p))
    assert(np.allclose(expected_q, q))
    assert(np.allclose(expected_s, s))
    

    # test extract_R
    pos = np.array([0, 103.99147034, 2.07609391])
    q = np.array([0.99561772, 0.09101666,  0.00978048, -0.01912292])
    m = compose_mat(pos, q)
    result = extract_R(m)
    expected = np.array([
        [0.9990773122642652,  -0.036297862774000135, -0.022956247011468537, 0],
        [0.03985860925165068,  0.9827005627154562,    0.1808615362846678,   0],
        [0.01599422977498581, -0.18160966163283088,   0.983240619286812,    0],
        [0,                    0,                     0,                    1]
    ])
    assert((expected == result).all())
    

    # test R_from_Q
    q = np.array([0.99561772, 0.09101666,  0.00978048, -0.01912292])
    result = R_from_Q(q)
    expected = np.array([
        [0.9990773122642652,  -0.036297862774000135, -0.022956247011468537, 0],
        [0.03985860925165068,  0.9827005627154562,    0.1808615362846678,   0],
        [0.01599422977498581, -0.18160966163283088,   0.983240619286812,    0],
        [0,                    0,                     0,                    1]
    ])
    assert(np.allclose(expected, result))


    # test unproject
    v = np.array([13.76, 23.41, 31.89])
    result = unproject(v)
    expected =  np.array([-0.4925567013678026, 0.3715069560853783, 3.0648148838306635])
    assert((expected == result).all())