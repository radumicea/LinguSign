import numpy as np
from numpy.typing import NDArray
from typing import Annotated, Literal


Vec = Annotated[NDArray[np.float64], Literal["N"]]
Vec3 = Annotated[NDArray[np.float64], Literal[3]]
Vec4 = Annotated[NDArray[np.float64], Literal[4]]
Mat4 = Annotated[NDArray[np.float64], Literal[4, 4]]

VecNx3 = Annotated[NDArray[np.float64], Literal["N", 3]]
VecNx4 = Annotated[NDArray[np.float64], Literal["N", 4]]
VecNxNx3 = Annotated[NDArray[np.float64], Literal["N", "N", 3]]