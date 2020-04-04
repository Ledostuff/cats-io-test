package com.example.cats.effect.algebra

import java.io.{File, InputStream, OutputStream}

trait FileCopier[F[_]] {

  def copy(in: File, out: File): F[Long]

  def transfer(in: InputStream, out: OutputStream): F[Long]

}
