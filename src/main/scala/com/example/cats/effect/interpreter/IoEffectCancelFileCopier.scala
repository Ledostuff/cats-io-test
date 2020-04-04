package com.example.cats.effect.interpreter

import java.io.{File, FileInputStream, FileOutputStream, InputStream, OutputStream}

import cats.effect.{Concurrent, IO, Resource, Sync}
import cats.effect.concurrent.Semaphore
import cats.implicits._

class IoEffectCancelFileCopier {

  private def inputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileInputStream] = {
    Resource.make {
      Sync[F].delay(new FileInputStream(f))
    } {inStream =>
      guard.withPermit {
        Sync[F].delay(inStream.close()).handleErrorWith(_ => Sync[F].unit)
      }
    }
  }

  private def outputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileOutputStream] = {
    Resource.make {
      Sync[F].delay(new FileOutputStream(f))
    } { outputStream =>
      guard.withPermit {
        Sync[F].delay(outputStream.close()).handleErrorWith(_ => Sync[F].unit)
      }
    }
  }

  private def inputOutputStreams[F[_]: Sync](in: File, out: File, guard: Semaphore[F]): Resource[F, (FileInputStream, FileOutputStream)] = {
    for {
      inStream <- inputStream(in, guard)
      outStream <- outputStream(out, guard)
    } yield (inStream, outStream)
  }

  def copy[F[_]](in: File, out: File)(implicit concurrent: Concurrent[F]): F[Long] = {
    for {
      guard <- Semaphore[F](1)
      count <- inputOutputStreams(in, out, guard).use { case (in, out) =>
        guard.withPermit(transfer(in, out))
      }
    } yield count
  }

  def transfer[F[_]: Sync](in: InputStream, out: OutputStream): F[Long] = {
    for {
      buffer <- Sync[F].pure(new Array[Byte](1024*10))
      count <- transmit(in, out, buffer, 0L)
    } yield count
  }

  private def transmit[F[_]: Sync](streamIn: InputStream, streamOut: OutputStream, buffer: Array[Byte], acc: Long): F[Long] = {
    for {
      amount <- Sync[F].delay(streamIn.read(buffer, 0, buffer.length))
      count <- if (amount > -1) Sync[F].delay(streamOut.write(buffer, 0, amount)) >> transmit(streamIn, streamOut, buffer, acc + amount)
                else Sync[F].pure(acc)
    } yield count
  }
}
