package website;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class ConexaoFtp {
	private static FTPClient ftp = null;
	private static ZipInputStream zinstream;

	private static String caminhoBase = "C:\\Users\\Jonathan\\Desktop\\teste\\";
	private static String nomePastaFinalPdv = "safepdv\\";
	private static String nomeArquivoVersaoPdv = "versao_pdv.txt";
	private static String caminhoBasePastaPdv = caminhoBase + nomePastaFinalPdv;
	
	private static String nomeArquivo;
	private static String nomeArquivoServidor;
	private static String enderecoServidor = "localhost";
	private static String usuario = "jonathan";
	private static String senha = "123456";
	private static VersaoPdv ultimaVersaoNoServidor;

	public ConexaoFtp(String host, String user, String pwd) throws Exception {
		ftp = new FTPClient();
		ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
		int reply;
		ftp.connect(host);
		reply = ftp.getReplyCode();
		if (!FTPReply.isPositiveCompletion(reply)) {
			ftp.disconnect();
			throw new Exception("Não foi possivel conectar no servidor");
		}
		ftp.login(user, pwd);
		ftp.setFileType(FTP.BINARY_FILE_TYPE);
		ftp.enterLocalActiveMode();
	}

	public static void main(String[] args) {
		try {
			ConexaoFtp ftpDownloader = new ConexaoFtp(enderecoServidor, usuario, senha);

			getUltimaVersaoPdvServidor();

			if (validarNecessidadeAtualizarPdv()) {
				ftpDownloader.downloadFile(nomeArquivoServidor, caminhoBase + nomeArquivo);
				ftpDownloader.disconnect();

				new File(caminhoBase).mkdir();
				descompactar();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Boolean validarNecessidadeAtualizarPdv() throws IOException {
		File file = new File(caminhoBase);
		
		Double versaoAtualPdv = -1.0;

		for (File arquivosRaiz:file.listFiles()) {

			// Achar a pasta do pdv
			if (arquivosRaiz.getName().equals(nomePastaFinalPdv)) {
				
				File fileDentroPasta = new File(arquivosRaiz.getAbsolutePath());

				for (File arquivosDentroPasta:fileDentroPasta.listFiles()) {

					// achar o arquivo de versão
					if (arquivosDentroPasta.getName().equals(nomeArquivoVersaoPdv)) {
						FileReader arq = new FileReader(arquivosDentroPasta.getAbsolutePath());
						BufferedReader lerArq = new BufferedReader(arq);

						String linha = lerArq.readLine(); 
						
						while (linha != null) {
							if(StringUtils.isNotEmpty(linha)){
								versaoAtualPdv = Double.parseDouble(linha);
							}
							
							linha = lerArq.readLine();
						}
						
						arq.close();
					}
				}
			}
		}

		
		if (versaoAtualPdv > 0) {
			
			if (getUltimaVersaoNoServidor().getVersaoArquivo() > versaoAtualPdv) {
				setNomeArquivo(getUltimaVersaoNoServidor().getNomeArquivo());
				setNomeArquivoServidor(getUltimaVersaoNoServidor().getNomeArquivo());
				return true;
			} else {
				return false;
			}
		}else{
			//Primeira Instação
			setNomeArquivo(getUltimaVersaoNoServidor().getNomeArquivo());
			setNomeArquivoServidor(getUltimaVersaoNoServidor().getNomeArquivo());
		}

		return true;
	}

	private static void getUltimaVersaoPdvServidor() throws IOException {
		String[] arq = ftp.listNames();

		List<VersaoPdv> listaVersoesPdv = new ArrayList<>();

		for (String f : arq) {
			listaVersoesPdv.add(new VersaoPdv(f));
		}

		Collections.sort(listaVersoesPdv, new Comparator<VersaoPdv>() {
			public int compare(VersaoPdv o1, VersaoPdv o2) {
				return o2.getVersaoArquivo().compareTo(o1.getVersaoArquivo());
			}
		});

		setUltimaVersaoNoServidor(listaVersoesPdv.get(0));
	}

	public static class VersaoPdv {
		String nomeArquivo;
		Double versaoArquivo;

		public VersaoPdv(String nomeArquivo) {
			setNomeArquivo(nomeArquivo);
			setVersaoArquivo(Double
					.parseDouble(nomeArquivo.substring(nomeArquivo.indexOf("(") + 1, nomeArquivo.lastIndexOf(")"))));
		}

		public String getNomeArquivo() {
			return nomeArquivo;
		}

		public void setNomeArquivo(String nomeArquivo) {
			this.nomeArquivo = nomeArquivo;
		}

		public Double getVersaoArquivo() {
			return versaoArquivo;
		}

		public void setVersaoArquivo(Double versaoArquivo) {
			this.versaoArquivo = versaoArquivo;
		}
	}

	private static void descompactar() throws IOException {
		File file = new File(caminhoBase + nomeArquivo);
		zinstream = new ZipInputStream(new FileInputStream(file));

		ZipEntry entry;
		
		while ((entry = zinstream.getNextEntry()) != null) {
			byte[] tmp = new byte[2048];
			BufferedOutputStream bos = null;
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			bos = new BufferedOutputStream(byteArrayOutputStream);
			int size = 0;
			while ((size = zinstream.read(tmp)) != -1) {
				bos.write(tmp, 0, size);
			}
			bos.flush();
			bos.close();

			String nomeArquivo = trocarNomeDiretorio(entry.getName());
			
			if (entry.isDirectory()) {
				nomeArquivo= nomeArquivo.replace("/", "\\")+"\\";
				new File(caminhoBasePastaPdv + nomeArquivo).mkdir();
			}
			
			salvarArquivo(caminhoBasePastaPdv +nomeArquivo, byteArrayOutputStream.toByteArray());
		}
		zinstream.close();
		file.delete();
	}
	
	private static String trocarNomeDiretorio(String localFilePath){
		localFilePath = localFilePath.substring(localFilePath.indexOf("/")+1,localFilePath.length());
		return localFilePath;
	}

	public static void salvarArquivo(String nomeArquivo, byte[] arquivo) {
		FileOutputStream file = null;
		try {
			file = new FileOutputStream(nomeArquivo);
			file.write(arquivo);
			file.flush();
			file.close();
		} catch (Exception exception) {
			exception.printStackTrace(System.out);
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void downloadFile(String remoteFilePath, String localFilePath) {
		try (FileOutputStream fos = new FileOutputStream(localFilePath)) {
			if (!this.ftp.retrieveFile(remoteFilePath, fos)) {
				System.err.println("Erro ao baixar arquivo");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (this.ftp.isConnected()) {
			try {
				this.ftp.logout();
				this.ftp.disconnect();
			} catch (IOException f) {
			}
		}
	}

	public static FTPClient getFtp() {
		return ftp;
	}

	public static void setFtp(FTPClient ftp) {
		ConexaoFtp.ftp = ftp;
	}

	public static ZipInputStream getZinstream() {
		return zinstream;
	}

	public static void setZinstream(ZipInputStream zinstream) {
		ConexaoFtp.zinstream = zinstream;
	}

	public static String getCaminhoBase() {
		return caminhoBase;
	}

	public static void setCaminhoBase(String caminhoBase) {
		ConexaoFtp.caminhoBase = caminhoBase;
	}

	public static String getNomeArquivo() {
		return nomeArquivo;
	}

	public static void setNomeArquivo(String nomeArquivo) {
		ConexaoFtp.nomeArquivo = nomeArquivo;
	}

	public static String getNomeArquivoServidor() {
		return nomeArquivoServidor;
	}

	public static void setNomeArquivoServidor(String nomeArquivoServidor) {
		ConexaoFtp.nomeArquivoServidor = nomeArquivoServidor;
	}

	public static String getEnderecoServidor() {
		return enderecoServidor;
	}

	public static void setEnderecoServidor(String enderecoServidor) {
		ConexaoFtp.enderecoServidor = enderecoServidor;
	}

	public static String getUsuario() {
		return usuario;
	}

	public static void setUsuario(String usuario) {
		ConexaoFtp.usuario = usuario;
	}

	public static String getSenha() {
		return senha;
	}

	public static void setSenha(String senha) {
		ConexaoFtp.senha = senha;
	}

	public static VersaoPdv getUltimaVersaoNoServidor() {
		return ultimaVersaoNoServidor;
	}

	public static void setUltimaVersaoNoServidor(VersaoPdv ultimaVersaoNoServidor) {
		ConexaoFtp.ultimaVersaoNoServidor = ultimaVersaoNoServidor;
	}

}
