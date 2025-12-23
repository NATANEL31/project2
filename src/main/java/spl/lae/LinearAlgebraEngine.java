package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.ArrayList;
import java.util.List;

public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();
    private SharedMatrix rightMatrix = new SharedMatrix();
    private TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        // TODO: create executor with given thread count
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        this.executor = new TiredExecutor(numThreads);
    }
    

    public ComputationNode run(ComputationNode computationRoot) {
        // TODO: resolve computation tree step by step until final matrix is produced
        computationRoot.associativeNesting();
        while (computationRoot.getNodeType() != ComputationNodeType.MATRIX) {
            ComputationNode nextNode = computationRoot.findResolvable();
            if (nextNode == null) {
                throw new IllegalStateException("Could not find a resolvable node in the tree");
            }
            loadAndCompute(nextNode);
            double[][] result = leftMatrix.readRowMajor();
            nextNode.resolve(result);
        }
        try {
            executor.shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Engine interrupted during shutdown");
        }

        return computationRoot;
    }
    

    public void loadAndCompute(ComputationNode node) {
        // TODO: load operand matrices
        // TODO: create compute tasks & submit tasks to executor
        ComputationNodeType type = node.getNodeType();
        List<ComputationNode> children = node.getChildren();
        List<Runnable> tasks = new ArrayList<>();

        switch (type) {
            case ADD:
                if (children.size() != 2) throw new IllegalArgumentException("ADD requires 2 operands");
                leftMatrix.loadRowMajor(children.get(0).getMatrix());
                rightMatrix.loadRowMajor(children.get(1).getMatrix());
                tasks = createAddTasks();
                break;
            case MULTIPLY:
                if (children.size() != 2) throw new IllegalArgumentException("MULTIPLY requires 2 operands");
                leftMatrix.loadRowMajor(children.get(0).getMatrix());
                rightMatrix.loadColumnMajor(children.get(1).getMatrix());
                tasks = createMultiplyTasks();
                break;
            case NEGATE:
                if (children.size() != 1) throw new IllegalArgumentException("NEGATE requires 1 operand");
                leftMatrix.loadRowMajor(children.get(0).getMatrix());
                tasks = createNegateTasks();
                break;

            case TRANSPOSE:
                if (children.size() != 1) throw new IllegalArgumentException("TRANSPOSE requires 1 operand");
                leftMatrix.loadRowMajor(children.get(0).getMatrix());
                tasks = createTransposeTasks();
                break;

            default:
                throw new UnsupportedOperationException("Unsupported operation: " + type);
        }    
        if (!tasks.isEmpty()) {
            executor.submitAll(tasks);
        }

    }

    public List<Runnable> createAddTasks() {
        // TODO: return tasks that perform row-wise addition
        List<Runnable> tasks = new ArrayList<>();
        int rows = leftMatrix.length();

        for (int i = 0; i < rows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector v1 = leftMatrix.get(rowIndex);
                SharedVector v2 = rightMatrix.get(rowIndex);
                v1.add(v2);
            });
        }
        return tasks;
    }

    public List<Runnable> createMultiplyTasks() {
        // TODO: return tasks that perform row × matrix multiplication
       List<Runnable> tasks = new ArrayList<>();
        int rows = leftMatrix.length();

        for (int i = 0; i < rows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector v1 = leftMatrix.get(rowIndex);
                v1.vecMatMul(rightMatrix);
            });
        }
        return tasks;
    }

    public List<Runnable> createNegateTasks() {
        // TODO: return tasks that negate rows
        List<Runnable> tasks = new ArrayList<>();
        int rows = leftMatrix.length();

        for (int i = 0; i < rows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector v = leftMatrix.get(rowIndex);
                v.negate();
            });
        }
        return tasks;
    }

    public List<Runnable> createTransposeTasks() {
        // TODO: return tasks that transpose rows
       List<Runnable> tasks = new ArrayList<>();
        int rows = leftMatrix.length();

        for (int i = 0; i < rows; i++) {
            final int rowIndex = i;
            tasks.add(() -> {
                SharedVector v = leftMatrix.get(rowIndex);
                v.transpose();
            });
        }
        return tasks;
    }

    public String getWorkerReport() {
        // TODO: return summary of worker activity
        if (executor == null) return "Executor not initialized";
        return executor.getWorkerReport();
    }
}
