package com.tencent.angel.ps.variable


import com.tencent.angel.common.Meta
import com.tencent.angel.matrix.psf.update.RandomNormal
import com.tencent.angel.matrix.psf.update.base.UpdateFunc
import com.tencent.angel.ps.server.data.request.{InitFunc, RandomNormalInitFunc}
import com.tencent.angel.ps.tensor.TensorMeta

abstract class Initializer {
  def getUpdateFunc(matId: Int, meta: Meta): UpdateFunc

  def getInitFunc(matId: Int, meta: Meta): InitFunc
}

class NormalInitializer(val mean: Double, val std: Double) extends Initializer {
  override def getInitFunc(matId: Int, meta: Meta): InitFunc = {
    new RandomNormalInitFunc(mean, std)
  }

  override def getUpdateFunc(matId: Int, meta: Meta): UpdateFunc = {
    val numRow = meta.getMatrixContext.getRowNum

    val originRows = meta match {
      case _: TensorMeta => numRow
      case vmeta: VariableMeta =>
        if (numRow == vmeta.getNumSlot + 1) {
          1
        } else {
          numRow / (vmeta.getNumSlot + 1)
        }
    }


    new RandomNormal(matId, 0, originRows, mean, std)
  }
}
