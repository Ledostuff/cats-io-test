package com.example.cats.effect.interpreter

import java.io.{File, FileInputStream, FileOutputStream, InputStream, OutputStream}

import cats.effect.{IO, Resource}
import cats.implicits._
import com.example.cats.effect.algebra.FileCopier

class IoEffectFileCopier extends FileCopier[IO]{

  private def inputStream(f:File): Resource[IO, FileInputStream] = {
    Resource.fromAutoCloseable {
      IO(new FileInputStream(f))
    }
  }

  private def outputStream(f: File): Resource[IO, FileOutputStream] = {
    Resource.make {
      IO(new FileOutputStream(f))
    } {outStream =>
      // release resources and handle errors during closing
      IO(outStream.close()).handleErrorWith(_ => IO.unit)
    }
  }

  private def inputOutputStreams(in: File, out:File): Resource[IO, (FileInputStream, FileOutputStream)] = {
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)
  }

  override def copy(in: File, out: File): IO[Long] = {
    inputOutputStreams(in, out).use {
      case (inStream, outStream) =>
        transfer(inStream, outStream)
    }
  }

  def copyBracket(in: File, out: File): IO[Long] = {
    val inIO: IO[InputStream] = IO(new FileInputStream(in))
    val outIO: IO[OutputStream] = IO(new FileOutputStream(out))

    (inIO, outIO).tupled.bracket {
      case (inStream, outStream) =>
        transfer(inStream, outStream)
    } {
      case (inStream, outStream) =>
        (IO(inStream.close()), IO(outStream.close()))
          .tupled
        .handleErrorWith(_ => IO.unit).void
    }
  }

  override def transfer(in: InputStream, out: OutputStream): IO[Long] = {
    for {
      buffer <- IO(new Array[Byte](1024 * 10))
      count <- transmit(in, out, buffer, 0L)
    } yield count
  }

  private def transmit(streamIn: InputStream, streamOut: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] = {
    for {
      amount <- IO(streamIn.read(buffer, 0, buffer.length))
      count <- if (amount > -1) IO(streamOut.write(buffer, 0, buffer.length)) >> transmit(streamIn, streamOut, buffer, acc + amount)
                else IO.pure(acc)
    } yield count
  }
}
