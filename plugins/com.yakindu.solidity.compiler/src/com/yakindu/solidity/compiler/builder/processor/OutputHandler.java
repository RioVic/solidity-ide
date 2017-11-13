package com.yakindu.solidity.compiler.builder.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.resources.IFile;

import com.google.inject.Inject;
/**
 * @author Florian Antony - Initial contribution and API
 */
public class OutputHandler {

	private final ExecutorService service;

	@Inject
	private MarkerOutputProcessor markerProcessor;

	@Inject
	private FileOutputProcessor outputFileWriter;

	public OutputHandler() {
		this.service = Executors.newFixedThreadPool(2);
	}

	public void shutdown() {
		service.shutdown();
	}

	public void handleError(final InputStream errorStream, IFile file) {
		processCompilerOutput(errorStream, markerProcessor, file);
	}

	public void handleOutput(InputStream inputStream, IFile file) {
		processCompilerOutput(inputStream, outputFileWriter, file);
	}

	private void processCompilerOutput(final InputStream stream, final ISolcOutputProcessor processor,
			final IFile file) {

		this.service.submit(new Runnable() {
			@Override
			public void run() {
				try (final BufferedReader output = new BufferedReader(new InputStreamReader(stream))) {
					processor.processLineForFile(output, file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
