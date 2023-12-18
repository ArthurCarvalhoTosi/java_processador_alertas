package org.example.processadorAlertas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.DatatypeConverter;


public class Processador extends JFrame implements ActionListener, Printable {
	
	private JLabel lbl1 = new JLabel("Arquivo XML");
	private JTextField txtArquivo = new JTextField(50);
	private JTextArea textArea = new JTextArea(5,50);
	private JButton btnExecutar = new JButton("Executar");
	private JButton btnLocalizar = new JButton("Localizar XML");
	private String lastDirectory = ".";
	private List<String> relatorio = new ArrayList<String>();
	private String itemDB = "";
	private String dtRegistroDB = "";
	private int cdEventoDB = 0;
	private int cdSitDB = 0;
	private Connection db;
	private Statement stm;
	private boolean globalError = false;
	private String lastTag = "";
	
	public static void main (String [] args) {
		Processador p = new Processador();
		p.setVisible(true);
	}
	
	public Processador() {
		super();
		initComponents();
	}
	
	private void initComponents() {
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(new FlowLayout());

		JPanel tela = new JPanel();
		
		this.getContentPane().add(tela);
		tela.setLayout(new BoxLayout(tela, BoxLayout.Y_AXIS));
		tela.setBackground(Color.WHITE);

		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		
		this.setTitle("Processador de alertas");
		tela.add(lbl1);
		tela.add(txtArquivo);
		btnExecutar.addActionListener(this);
		btnExecutar.setActionCommand("executar");
		btnLocalizar.addActionListener(this);
		btnLocalizar.setActionCommand("localizar");
		tela.add(btnLocalizar);
		tela.add(btnExecutar);
		
		textArea = new JTextArea(5, 20);
		JScrollPane scrollPane = new JScrollPane(textArea); 
		textArea.setEditable(false);
		
		tela.add(scrollPane);
		
		this.pack();
		int w = this.getSize().width;
		int h = this.getSize().height;
		int x = (dim.width-w)/2;
		int y = (dim.height-h)/2;
		this.setLocation(x, y);
	}

	public void actionPerformed(ActionEvent arg0) {
		if (arg0.getActionCommand().equals("executar")) {
			if (txtArquivo.getText() != null && txtArquivo.getText().length() > 0) {
				processarXML();
			}
			else {
				textArea.setText(textArea.getText() + "\r\nSelecione o arquivo XML.");
			}
		}
		else {
			JFileChooser chooser = new JFileChooser(lastDirectory);
			chooser.setDialogTitle("Selecionar arquivo XML");
		    FileNameExtensionFilter filter = new FileNameExtensionFilter(
		        "UI files", "xml");
		    chooser.setFileFilter(filter);
		    int returnVal = chooser.showOpenDialog(this);
		    if(returnVal == JFileChooser.APPROVE_OPTION) {;
			    lastDirectory = chooser.getSelectedFile().getPath();
			    txtArquivo.setText(chooser.getSelectedFile().getPath());
		    }
		}
		
	}

	private void processarXML() {
		/*
		 * Banco: 
		 * CREATE TABLE alerta (
			item varchar(100),
			dtreg varchar(50),
			evento integer,
			codsit integer)
		 */
		try {
			FileReader fr = new FileReader(txtArquivo.getText());
			BufferedReader br = new BufferedReader(fr);
			String linha = null;
			int contaLinha = 0;
			
			Class.forName("org.hsqldb.jdbc.JDBCDriver");
			db = DriverManager.getConnection(
			          "jdbc:hsqldb:file:c:/processador/testedb", "SA", "");
			stm = db.createStatement();
			stm.execute("START TRANSACTION");
			
			while ((linha = br.readLine()) != null) {
				contaLinha++;
				String tag = null;
				int iTag = linha.indexOf('<');
				if (iTag < 0) {
					textArea.setText(textArea.getText() + "\r\nErro na linha: " + contaLinha);
					break;
				}
				else {
					int fTag = linha.indexOf('>', iTag + 1);
					if (fTag < 0) {
						textArea.setText(textArea.getText() + "\r\nErro na linha: " + contaLinha);
						break;
					}
					else {
						tag = linha.substring(iTag+1, fTag);
					}
				}
				if (tag.charAt(0) != '/') {
					textArea.setText(textArea.getText() + "\r\nLinha: " + contaLinha + " TAG: " + tag);
					String conteudo = tag;
					int fimTag = tag.indexOf(' ');
					if (fimTag < 0) {
						fimTag = tag.indexOf('/');
					}
					if (fimTag > 0) {
						conteudo = tag.substring(0, fimTag);
					}
					if (conteudo.indexOf("alertas") < 0) {
						processarTag(conteudo, linha);
						if (globalError) {
							return;
						}
					}
 				}
			}
			br.close();
			fr.close();
			if (!globalError) {
				stm.execute("COMMIT");
			}
			
			db.close();
			PrinterJob job = PrinterJob.getPrinterJob();
			job.setPrintable(this);
			boolean doPrint = job.printDialog();
			if (doPrint) {
				job.print();
			}
			else {
				textArea.setText(textArea.getText() + "\r\nProcessamento cancelado.");
			}
		} 
		catch (Exception e) {
			textArea.setText(textArea.getText() + "\r\nException: " + e.getMessage());
			globalError = true;
			try {
				stm.execute("ROLLBACK");
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
	}

	private void processarTag(String conteudo, String linha) {
		try {
			int pIni = -1;
			int pFim = -1;
			int tam = conteudo.length();
			String texto = null;
			pIni = conteudo.indexOf("listaAlertas");
			String saida = "";
			if (pIni >= 0) {
				// Inicio do conjunto
				tam = "listaAlertas".length();
				saida = "Alerta:";
				lastTag = "listaAlertas";
			}
			else {
				// CÛdigo da Filial
				// Item
				pIni = conteudo.indexOf("idItemConfiguracao");
				if (pIni >= 0) {
					tam = "idItemConfiguracao".length();
					pIni = linha.indexOf("idItemConfiguracao") + tam + 1;
					pFim = linha.lastIndexOf('<');
					texto = linha.substring(pIni, pFim);
					if (texto.length() < 5) {
						throw new Exception("item");
					}
					if (!lastTag.equals("listaAlertas")) {
						throw new Exception("item fora de ordem");
					}
					lastTag = "idItemConfiguracao";
					itemDB = texto;
					saida = "Item de configuração: " + texto;
				}
				else {
					pIni = conteudo.indexOf("dataHoraRegistro");
					if (pIni >= 0) {
						tam = "dataHoraRegistro".length();
						pIni = linha.indexOf("dataHoraRegistro") + tam + 1;
						pFim = linha.lastIndexOf('<');
						texto = linha.substring(pIni, pFim);
						try {
							Calendar calendar = DatatypeConverter.parseDateTime(texto);
						}
						catch (Exception ex) {
							throw new Exception("data");
						}
						if (!lastTag.equals("idItemConfiguracao")) {
							throw new Exception("data fora de ordem");
						}
						lastTag = "dataHoraRegistro";
						dtRegistroDB = texto;
						saida = "Data / Hora Registro: " + texto;
					}
					else {
						pIni = conteudo.indexOf("codigoDeEvento");
						if (pIni >= 0) {
							tam = "codigoDeEvento".length();
							pIni = linha.indexOf("codigoDeEvento") + tam + 1;
							pFim = linha.lastIndexOf('<');
							texto = linha.substring(pIni, pFim);
							cdEventoDB = Integer.parseInt(texto);
							switch(cdEventoDB) {
							case 1: 
							case 2:
							case 3:
								break;
							default:
								throw new Exception("codigoEvento");
							}
							if (!lastTag.equals("dataHoraRegistro")) {
								throw new Exception("evento fora de ordem");
							}
							lastTag = "codigoEvento";
							saida = "Código De Evento: " + texto;
						}
						else {
							// ˙ltimo tag
							pIni = conteudo.indexOf("codigoDeSituacao");
							if (pIni >= 0) {
								tam = "codigoDeSituacao".length();
								pIni = linha.indexOf("codigoDeSituacao") + tam + 1;
								pFim = linha.lastIndexOf('<');
								texto = linha.substring(pIni, pFim);
								cdSitDB = Integer.parseInt(texto);
								saida = "Código De Situação: " + texto;
								if (cdSitDB < 0 || cdSitDB > 2) {
									throw new Exception("codigoSituacao");
								}
								
								if (!lastTag.equals("codigoEvento")) {
									throw new Exception("codigoSituacao fora de ordem");
								}

								
								// Gravar
								
								String sql = "insert into alerta (item, dtreg, evento, codsit) " + 
											" values('" + itemDB + "','" + dtRegistroDB + "'," + 
											cdEventoDB + "," + cdSitDB + ")";
								stm.execute(sql);
							}
							else {
								textArea.setText(textArea.getText() + "\r\nTag inv·lido: " + conteudo);
							}
						}
					}
				}
			}
			
			relatorio.add(saida);			
		}
		catch (Exception ex) {
			try {
				textArea.setText(textArea.getText() + "\r\nException: " + ex.getMessage());
				stm.execute("ROLLBACK");
				globalError = true;
			} catch (SQLException e) {

				e.printStackTrace();
			}
		}

	}

	public int print(Graphics g, PageFormat pf, int pagina)
			throws PrinterException {
		int retorno = PAGE_EXISTS;
		int totalPaginas = relatorio.size() / 30;
		if (relatorio.size() % 30 > 0) {
			totalPaginas++;
		}
		if (pagina > totalPaginas) {
			retorno = NO_SUCH_PAGE;
		}
		else {
			int inicial = pagina * 30;
			int contaLinhas = 0;
			Graphics2D g2d = (Graphics2D)g;
		    g2d.translate(pf.getImageableX(), pf.getImageableY());
			for (int x = inicial; x < relatorio.size(); x++) {
				contaLinhas++;
				if (contaLinhas > 30) {
					break;
				}
				g.drawString(relatorio.get(x), 40, 50 + contaLinhas * 14);
			}
		}
		return retorno;
	}

}
