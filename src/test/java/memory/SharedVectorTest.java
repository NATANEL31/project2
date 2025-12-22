package memory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SharedVectorTest {

    // ---------- Constructor / getters ----------

    @Test
    void constructor_nullVector_throws() {
        assertThrows(NullPointerException.class,
                () -> new SharedVector(null, VectorOrientation.ROW_MAJOR));
    }

    @Test
    void constructor_nullOrientation_throws() {
        assertThrows(NullPointerException.class,
                () -> new SharedVector(new double[]{1, 2}, null));
    }

    @Test
    void get_returnsElement() {
        SharedVector v = new SharedVector(new double[]{10.0, 20.0, -3.5}, VectorOrientation.ROW_MAJOR);
        assertEquals(10.0, v.get(0));
        assertEquals(20.0, v.get(1));
        assertEquals(-3.5, v.get(2));
    }

    @Test
    void get_outOfBounds_throws() {
        SharedVector v = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> v.get(-1));
    }

    @Test
    void length_returnsLength() {
        SharedVector v = new SharedVector(new double[]{1.0, 2.0, 3.0, 4.0}, VectorOrientation.ROW_MAJOR);
        assertEquals(4, v.length());
    }

    @Test
    void getOrientation_returnsOrientation() {
        SharedVector v = new SharedVector(new double[]{1.0}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
    }

    // ---------- transpose ----------

    @Test
    void transpose_togglesOrientation() {
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    // ---------- negate ----------

    @Test
    void negate_negatesAllElements_inPlace() {
        SharedVector v = new SharedVector(new double[]{1.0, -2.5, 0.0}, VectorOrientation.ROW_MAJOR);
        v.negate();
        assertEquals(-1.0, v.get(0));
        assertEquals(2.5, v.get(1));
        assertEquals(-0.0, v.get(2)); // -0.0 is fine; equals compares as 0.0 == -0.0 true in doubles
    }

    @Test
    void negate_emptyVector_ok() {
        SharedVector v = new SharedVector(new double[]{}, VectorOrientation.ROW_MAJOR);
        v.negate();
        assertEquals(0, v.length());
    }

    // ---------- add ----------

    @Test
    void add_addsElementwise() {
        SharedVector a = new SharedVector(new double[]{1.0, 2.0, 3.0}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{10.0, -2.0, 0.5}, VectorOrientation.ROW_MAJOR);

        a.add(b);

        assertEquals(11.0, a.get(0));
        assertEquals(0.0, a.get(1));
        assertEquals(3.5, a.get(2));
    }

    @Test
    void add_nullOther_throws() {
        SharedVector a = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(NullPointerException.class, () -> a.add(null));
    }

    @Test
    void add_lengthMismatch_throws() {
        SharedVector a = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.add(b));
    }

    @Test
    void add_orientationMismatch_throws() {
        SharedVector a = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{2.0}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.add(b));
    }

    // ---------- dot ----------

    @Test
    void dot_computesRowDotColumn() {
        SharedVector row = new SharedVector(new double[]{1.0, 2.0, 3.0}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{4.0, -1.0, 0.5}, VectorOrientation.COLUMN_MAJOR);

        double ans = row.dot(col);

        // 1*4 + 2*(-1) + 3*(0.5) = 4 - 2 + 1.5 = 3.5
        assertEquals(3.5, ans, 1e-12);
    }

    @Test
    void dot_nullOther_throws() {
        SharedVector row = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(NullPointerException.class, () -> row.dot(null));
    }

    @Test
    void dot_wrongOrientations_throws() {
        SharedVector a = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        SharedVector b = new SharedVector(new double[]{3.0, 4.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> a.dot(b));

        SharedVector c = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.COLUMN_MAJOR);
        SharedVector d = new SharedVector(new double[]{3.0, 4.0}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> c.dot(d));
    }

    @Test
    void dot_lengthMismatch_throws() {
        SharedVector row = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{3.0}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> row.dot(col));
    }

    @Test
    void dot_emptyVectors_isZero() {
        SharedVector row = new SharedVector(new double[]{}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(0.0, row.dot(col), 1e-12);
    }

    // ---------- vecMatMul ----------
    //
    // NOTE: These tests assume SharedMatrix has at least:
    //   - constructor from SharedVector[] and an orientation, OR similar
    //   - get(int) returning a SharedVector
    //   - length() returning number of vectors (columns if COLUMN_MAJOR)
    //   - getOrientation()
    //
    // If your SharedMatrix constructor differs, tell me its API and I’ll adjust.

    private static SharedMatrix makeColumnMajorMatrix(double[][] cols) {
        // cols[j][k] is entry in column j, row k
        SharedVector[] vectors = new SharedVector[cols.length];
        for (int j = 0; j < cols.length; j++) {
            vectors[j] = new SharedVector(cols[j], VectorOrientation.COLUMN_MAJOR);
        }
        return new SharedMatrix(vectors, VectorOrientation.COLUMN_MAJOR);
    }

    @Test
    void vecMatMul_simpleCase() {
        // row = [1,2]
        SharedVector row = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);

        // matrix 2x3 in column-major storage:
        // columns:
        // c0 = [1,0], c1 = [0,1], c2 = [3,4]
        // so matrix (rows):
        // [1 0 3]
        // [0 1 4]
        SharedMatrix mat = makeColumnMajorMatrix(new double[][]{
                {1.0, 0.0},
                {0.0, 1.0},
                {3.0, 4.0}
        });

        row.vecMatMul(mat);

        // result is 1x3:
        // [1,2] * mat = [1*1+2*0, 1*0+2*1, 1*3+2*4] = [1,2,11]
        assertEquals(VectorOrientation.ROW_MAJOR, row.getOrientation());
        assertEquals(3, row.length());
        assertEquals(1.0, row.get(0), 1e-12);
        assertEquals(2.0, row.get(1), 1e-12);
        assertEquals(11.0, row.get(2), 1e-12);
    }

    @Test
    void vecMatMul_nullMatrix_throws() {
        SharedVector row = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);
        assertThrows(NullPointerException.class, () -> row.vecMatMul(null));
    }

    @Test
    void vecMatMul_rowWrongOrientation_throws() {
        SharedVector col = new SharedVector(new double[]{1.0}, VectorOrientation.COLUMN_MAJOR);
        SharedMatrix mat = makeColumnMajorMatrix(new double[][]{{1.0}});
        assertThrows(IllegalArgumentException.class, () -> col.vecMatMul(mat));
    }

    @Test
    void vecMatMul_matrixWrongOrientation_throws() {
        SharedVector row = new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR);

        // Build a "matrix" with wrong orientation tag.
        SharedVector[] rows = new SharedVector[]{
                new SharedVector(new double[]{1.0}, VectorOrientation.ROW_MAJOR)
        };
        SharedMatrix rowMajor = new SharedMatrix(rows, VectorOrientation.ROW_MAJOR);

        assertThrows(IllegalArgumentException.class, () -> row.vecMatMul(rowMajor));
    }

    @Test
    void vecMatMul_dimensionMismatch_throws() {
        // row length 2
        SharedVector row = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);

        // matrix whose columns have length 3
        SharedMatrix mat = makeColumnMajorMatrix(new double[][]{
                {1.0, 0.0, 0.0}
        });

        assertThrows(IllegalArgumentException.class, () -> row.vecMatMul(mat));
    }

    // ---------- locking sanity test ----------
    // This test checks that a writer lock blocks a reader, which is an important concurrency invariant.
    // It doesn't prove everything, but it’s a good “meaningful” test.

    @Test
    void readIsBlockedWhileWriteLockHeld() throws Exception {
        SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch writerHasLock = new CountDownLatch(1);
            CountDownLatch allowWriterRelease = new CountDownLatch(1);
            AtomicBoolean readerFinished = new AtomicBoolean(false);

            Future<?> writer = exec.submit(() -> {
                v.writeLock();
                try {
                    writerHasLock.countDown();
                    // Hold lock until test releases us
                    allowWriterRelease.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    v.writeUnlock();
                }
            });

            assertTrue(writerHasLock.await(1, TimeUnit.SECONDS), "writer did not acquire lock in time");

            Future<?> reader = exec.submit(() -> {
                // Should block until writer releases
                v.get(0);
                readerFinished.set(true);
            });

            // Give reader a brief moment; it should NOT finish yet.
            Thread.sleep(100);
            assertFalse(readerFinished.get(), "reader should be blocked while writer holds lock");

            // Release writer; now reader should finish.
            allowWriterRelease.countDown();
            writer.get(1, TimeUnit.SECONDS);
            reader.get(1, TimeUnit.SECONDS);

            assertTrue(readerFinished.get());
        } finally {
            exec.shutdownNow();
        }
    }
}
