/*******************************************************************************
 * Copyright (c) 2014 Sebastian Stenzel, Markus Kreusch
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 * 
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *     Markus Kreusch - Refactored WebDavMounter to use strategy pattern
 ******************************************************************************/
package org.cryptomator.ui.util.mount;

import java.net.URI;

import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.ui.util.command.CommandResult;
import org.cryptomator.ui.util.command.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LinuxGvfsWebDavMounter implements WebDavMounterStrategy {

	public static final Logger LOG = LoggerFactory.getLogger(LinuxGvfsWebDavMounter.class);
	@Override
	public boolean shouldWork() {
		if (SystemUtils.IS_OS_LINUX) {
			final Script checkScripts = Script.fromLines("which gvfs-mount xdg-open");
			try {
				checkScripts.execute();
				return true;
			} catch (CommandFailedException e) {
				return false;
			}
		} else {
			return false;
		}
	}
	
	@Override
	public void warmUp(int serverPort) {
		// no-op
	}

	@Override
	public WebDavMount mount(URI uri, String name) throws CommandFailedException {
		final Script mountScript = Script.fromLines(
				"set -x",
				"gvfs-mount \"dav:$DAV_SSP\"")
				.addEnv("DAV_SSP", uri.getRawSchemeSpecificPart());
		final Script testMountStillExistsScript = Script.fromLines(
				"set -x",
				"test `gvfs-mount --list | grep \"$DAV_SSP\" | wc -l` -eq 1")
				.addEnv("DAV_SSP", uri.getRawSchemeSpecificPart());
		final Script unmountScript = Script.fromLines(
				"set -x",
				"gvfs-mount -u \"dav:$DAV_SSP\"")
				.addEnv("DAV_SSP", uri.getRawSchemeSpecificPart());
		mountScript.execute();
		try{
			openFMWithWebdavSchema("dav", uri).execute();
		}catch(CommandFailedException exception){
			LOG.debug("Openinig webdav with dav schema name failed, trying webdav schema name");
			openFMWithWebdavSchema("webdav", uri).execute();
		}
		return new AbstractWebDavMount() {
			@Override
			public void unmount() throws CommandFailedException {
				boolean mountStillExists;
				try {
					testMountStillExistsScript.execute();
					mountStillExists = true;
				} catch(CommandFailedException e) {
					mountStillExists = false;
				}
				// only attempt unmount if user didn't unmount manually:
				if (mountStillExists) {
					unmountScript.execute();
				}
			}
		};
	}

	private Script openFMWithWebdavSchema(String schemaName, URI uri){
		return Script.fromLines(
				"set -x",
				"xdg-open \""+schemaName+":$DAV_SSP\"")
				.addEnv("DAV_SSP", uri.getRawSchemeSpecificPart());
	}

}
