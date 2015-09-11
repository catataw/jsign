package com.github.jsign;

import java.awt.Frame;
import java.awt.Image;
import java.io.File;
import java.net.URL;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.github.jsign.gui.DlgCertificateNotFound;
import com.github.jsign.gui.DlgConfiguration;
import com.github.jsign.gui.DlgConfigurationWindows;
import com.github.jsign.interfaces.SignLog;
import com.github.jsign.interfaces.SignLogProgress;
import com.github.jsign.interfaces.SignProgress;
import com.github.jsign.keystore.KeyStoreHelper;
import com.github.jsign.keystore.MSCAPIKeyStoreHelper;
import com.github.jsign.manager.Manager;
import com.github.jsign.model.Configuration;
import com.github.jsign.model.MessageToSign;
import com.github.jsign.model.SignedMessage;
import com.github.jsign.util.FileUtils;
import com.github.jsign.util.JFrameUtils;

public class JSign implements SignLogProgress {

	private DlgConfiguration dlgConfiguration;
	private DlgConfigurationWindows dlgConfigurationWindows;
	private Configuration configuration;
	private SignProgress progress;
	private SignLog log;
	private boolean allowsCoSigning;
	private boolean allowsPkcs12Certificate = true;
	private Manager manager = new Manager();
	private KeyStoreHelper keyStore;
	private DlgCertificateNotFound dlgCertificateNotFound;
	
	public JSign() throws Exception {
		this(null);
	}
	
	public JSign(Frame parent) throws Exception {
		try {						
			Security.addProvider(new BouncyCastleProvider());
						
			try {
				this.configuration = manager.getConfigurationManager().loadConfigurations();
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
				this.configuration = new Configuration();
			}
			
			dlgConfiguration = new DlgConfiguration(parent, true, this);
			dlgConfiguration.setAlwaysOnTop(true);
			dlgConfigurationWindows = new DlgConfigurationWindows(parent, true);
			dlgConfigurationWindows.setAlwaysOnTop(true);
			dlgCertificateNotFound = new DlgCertificateNotFound(parent, true);
			dlgCertificateNotFound.setAlwaysOnTop(true);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Erro na inicialização do assinador, mensagem interna: " + e.getMessage());
		}
	}

	public SignProgress getProgress() {
		return progress;
	}

	public void setProgress(SignProgress progress) {
		this.progress = progress;
	}

	public SignLog getLog() {
		return log;
	}

	public void setLog(SignLog log) {
		this.log = log;
	}
	
	public SignedMessage signFile(File file, boolean attached) throws Exception {
				
		if (file == null) {
			throw new Exception("Por favor, selecione algum arquivo para realizar a assinatura digital!");
		}
	
		MessageToSign messageToSign = new MessageToSign(file.getName(), FileUtils.getFileBytes(file));
					
		return signMessage(messageToSign, attached);
	}
	
	public SignedMessage signMessage(MessageToSign messageToSign, boolean attached) throws Exception {
		
		List<SignedMessage> signMessages = signMessages(Arrays.asList(messageToSign), attached);
		
		return signMessages.get(0);
	}
	
	public List<SignedMessage> signMessages(List<MessageToSign> messages, boolean attached) throws Exception {
		
		if (this.keyStore == null) {
			this.keyStore = initKeyStore();
		}
		
		return getManager().getSignManager().signMessages(this.keyStore, messages, attached, isAllowsCoSigning(), this);
	}
	
	public KeyStoreHelper initKeyStore() throws Exception {
		
		KeyStoreHelper keyStoreHelper = null;
		
		// Verifica se existe algum certificado configurado, se houver utiliza o certificado
		if (this.configuration.isDefinedKeyStoreType()) {
			keyStoreHelper = getManager().getConfigurationManager().loadKeyStoreHelperByConfiguration(configuration);		
		}
		
		if (keyStoreHelper == null) {

			keyStoreHelper = configKeyStore();
			
			if (keyStoreHelper == null) {
				throw new Exception("Por favor, para realizar a assinatura deve-se configurar um certificado digital!");
			}			
		}
		
		return keyStoreHelper;
	}
	
	private KeyStoreHelper configKeyStore() {
		
		// Se o sistema operacional for windows verifica se existe certificados na MSCAPI, se houver retorna lista dos certificados  
		List<MSCAPIKeyStoreHelper> keyStoresHelpersAvailableMsCapi = getManager().getConfigurationManager().getKeyStoresHelpersAvailableOnMsCapi();
		
		KeyStoreHelper keyStoreHelper = null;
		
		if (!keyStoresHelpersAvailableMsCapi.isEmpty()) {
			keyStoreHelper = showDlgConfigurationWindows(keyStoresHelpersAvailableMsCapi);
		}
		else {
			keyStoreHelper = showDlgCertificateNotFound();
		}
		
		return keyStoreHelper;		
	}

	private KeyStoreHelper showDlgCertificateNotFound() {

		dlgCertificateNotFound.start(getManager().getConfigurationManager().getTokensDriversInstalledOnSystem(configuration));
		
		if (dlgCertificateNotFound.getReturnStatus() == DlgCertificateNotFound.RET_TRY_AGAIN) {
			return configKeyStore();
		}
		else if (dlgCertificateNotFound.getReturnStatus() == DlgCertificateNotFound.RET_OPEN_DLG_CONFIGURATION) {
			return showDlgConfiguration();
		}
		else {
			return null;
		}
	}

	public void resetKeyStore() {
		this.keyStore = null;
	}
	
	public KeyStoreHelper showDlgConfiguration() {
		return showDlgConfiguration(true);
	}
				
	public KeyStoreHelper showDlgConfiguration(boolean loadKeyStoreHelper) {		
		
		dlgConfiguration.start(loadKeyStoreHelper);
		
		if (dlgConfiguration.getReturnStatus() == DlgConfiguration.RET_OK) {
			
			KeyStoreHelper keyStoreHelper = dlgConfiguration.getKeyStoreHelper();
			
			updateKeyStoreHelper(keyStoreHelper);	    	
			
			return keyStoreHelper;
		}
		else {
			return null;
		}
	}

	private void updateKeyStoreHelper(KeyStoreHelper keyStoreHelper) {

		this.configuration.updateKeyStoreHelper(keyStoreHelper);
    	
    	try {
    		getManager().getConfigurationManager().writeConfiguration(this.configuration);
    	}
    	catch (Exception e) {
    		JFrameUtils.showErro("Erro", "Erro ao persistir as configurações!\nMensagem Interna: " + e.getMessage());
    	}
	}

	private KeyStoreHelper showDlgConfigurationWindows(List<MSCAPIKeyStoreHelper> keyStoresHelpersAvailableMsCapi) {
		
		dlgConfigurationWindows.start(keyStoresHelpersAvailableMsCapi);
		
		if (dlgConfigurationWindows.getReturnStatus() == DlgConfigurationWindows.RET_FINISH) {
						
			KeyStoreHelper keyStoreHelper = dlgConfigurationWindows.getKeyStoreHelper();
			
			updateKeyStoreHelper(keyStoreHelper);
			
			return keyStoreHelper;
		}
		else if (dlgConfigurationWindows.getReturnStatus() == DlgConfigurationWindows.RET_OPEN_DLG_CONFIGURATION) {
			return showDlgConfiguration();
		}
		else {
			return null;
		}	
	}

	public boolean isAllowsCoSigning() {
		return allowsCoSigning;
	}

	public void setAllowedCoSign(boolean allowsCoSigning) {
		this.allowsCoSigning = allowsCoSigning;
	}
	
	@Override
	public void printProgress(String msg) {
        if (progress != null) {
            progress.printProgress(msg);
        }
	}

	@Override
	public void printLogAndProgress(String msg) {
		printProgress(msg);
		printLog(msg);
	}	
	
	@Override
	public void printLog(String msg) {
        if (log != null) {
            log.printLog(msg);
        }
	}

	public Manager getManager() {
		return manager;
	}
		
	public Configuration getConfiguration() {
		return configuration;
	}

	public boolean isAllowsPkcs12Certificate() {
		return allowsPkcs12Certificate;
	}

	public void setAllowsPkcs12Certificate(boolean allowsPkcs12Certificate) {
		this.allowsPkcs12Certificate = allowsPkcs12Certificate;
	}

	public void  setDlgConfigurationAlwaysOnTop(boolean value) {
		this.dlgConfiguration.setAlwaysOnTop(true);
	}

	public void writeConfiguration(Configuration configuration) throws Exception {
		this.configuration = configuration;
		this.getManager().getConfigurationManager().writeConfiguration(configuration);
	}
	
	public void clearConfiguration() {
		this.configuration = this.getManager().getConfigurationManager().clearConfiguration();
	}

	public void setUrlDriversInstallationHelpPage(URL url) {
		dlgCertificateNotFound.setUrlDriversInstallationHelpPage(url);
	}
	
	public void setIconImage(Image image) {
		dlgConfiguration.setIconImage(image);
		dlgConfigurationWindows.setIconImage(image);
		dlgCertificateNotFound.setIconImage(image);
	}
}