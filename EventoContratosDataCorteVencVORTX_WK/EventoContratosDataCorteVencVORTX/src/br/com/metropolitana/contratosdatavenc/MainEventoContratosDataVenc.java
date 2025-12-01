package br.com.metropolitana.contratosdatavenc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class MainEventoContratosDataVenc implements EventoProgramavelJava{
	
    JapeWrapper parceiroDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);
    JapeWrapper financeiroDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
    JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
    JapeWrapper movSinteticosDAO = JapeFactory.dao("AD_FINSINTETICOSGRAFENO");
	JapeWrapper configDAO = JapeFactory.dao("AD_CONFIGSINTGRAF");


	@Override
	public void afterDelete(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void afterInsert(PersistenceEvent event) throws Exception {
	    
	    DynamicVO financeiroVO = (DynamicVO) event.getVo();
	    
	    if (deveAtualizarDataVencimento(financeiroVO)) {
	        Timestamp dataVencimento = calcularDataVencimento(financeiroVO);
	        
	        if (dataVencimento != null) {
	            atualizarDataVencimento(financeiroVO, dataVencimento);
	        }
	    }
	    
	    if(deveAtualizarGrafeno(financeiroVO)) {
	    	atualizarInformacoesGrafeno(financeiroVO);
	    }
	}
	
	private void atualizarInformacoesGrafeno(DynamicVO financeiroVO) throws Exception {
	    
	    BigDecimal nunota = financeiroVO.asBigDecimal("NUNOTA");
	    DynamicVO cabVO = cabDAO.findByPK(financeiroVO.asBigDecimal("NUNOTA"));
	    
	    boolean isOriginador = determinarSeOriginador(nunota);
	    
	    EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
	    NativeSql nativeSql = new NativeSql(dwfFacade.getJdbcWrapper());
	    
	    Timestamp dtVencOriginador = calcularDataComDiasCorridos(new Date(), 15);
	    Timestamp dtNeg = new Timestamp(System.currentTimeMillis());
	    
	    nativeSql.setNamedParameter("NUFIN", financeiroVO.asBigDecimal("NUFIN"));
	    nativeSql.setNamedParameter("CODTIPTIT", isOriginador ? new BigDecimal(4) : new BigDecimal(22));
	    nativeSql.setNamedParameter("CODBCO", isOriginador ? new BigDecimal(310) : new BigDecimal(274));
	    nativeSql.setNamedParameter("CODCTABCOINT", isOriginador ? new BigDecimal(99) : new BigDecimal(100));
	    nativeSql.setNamedParameter("DTVENC", isOriginador ? dtVencOriginador : dtNeg);
	    nativeSql.setNamedParameter("AD_IDSINTETICO", cabVO.asBigDecimal("AD_IDSINTETICO"));
	    
	    nativeSql.executeUpdate(
	        "UPDATE TGFFIN SET CODTIPTIT = :CODTIPTIT, " +
	        "CODBCO = :CODBCO, " +
	        "CODCTABCOINT = :CODCTABCOINT, " +
	        "DTVENC = :DTVENC, " +
	        "AD_IDSINTETICO = :AD_IDSINTETICO " +
	        "WHERE NUFIN = :NUFIN"
	    );
	}


	private boolean determinarSeOriginador(BigDecimal nunota) throws Exception {
	    DynamicVO sinteticoVO = movSinteticosDAO.findOne("NUNOTA = ?", nunota);
	    
	    return sinteticoVO != null && "S".equals(sinteticoVO.asString("ORIGINADOR"));
	}

	private Timestamp calcularDataComDiasCorridos(Date dataBase, int dias) {
	    Instant instant = dataBase.toInstant();
	    LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault())
	                                        .toLocalDateTime()
	                                        .plusDays(dias);
	    return Timestamp.valueOf(localDateTime);
	}

	private boolean deveAtualizarDataVencimento(DynamicVO financeiroVO) throws Exception {
	    return (financeiroVO.asBigDecimal("NUMCONTRATO") != null && 
	            financeiroVO.asBigDecimal("NUMCONTRATO").compareTo(BigDecimal.ZERO) != 0) || 
	            existefaturHH(financeiroVO.asBigDecimal("NUNOTA"));
	}
	
	private boolean deveAtualizarGrafeno(DynamicVO financeiroVO) throws Exception {
		
		if(financeiroVO.asBigDecimal("NUNOTA") == null) {
			return false;
		}
	    DynamicVO cabVO = cabDAO.findByPK(financeiroVO.asBigDecimal("NUNOTA"));
	    
	    return cabVO != null && cabVO.asBigDecimal("AD_IDSINTETICO") != null;
	
	}

	private Timestamp calcularDataVencimento(DynamicVO financeiroVO) throws Exception {
	    DynamicVO parceiroVO = parceiroDAO.findByPK(financeiroVO.asBigDecimal("CODPARC"));
	    
	    if (parceiroVO == null) {
	        return null;
	    }
	    
	    LocalDate dataVencimento = null;
	    LocalDate hoje = LocalDate.now();
	    
	    if (parceiroVO.asBigDecimal("AD_DTCORTE_VENC") != null) {
	        int dataCorte = parceiroVO.asBigDecimal("AD_DTCORTE_VENC").intValue();
	        LocalDate dataCorteAtual = LocalDate.of(hoje.getYear(), hoje.getMonth(), dataCorte);
	        
	        if (ChronoUnit.DAYS.between(hoje, dataCorteAtual) < 15) {
	            dataVencimento = hoje.plusDays(15);
	        } else {
	            dataVencimento = dataCorteAtual;
	        }
	    } 
	    else if (parceiroVO.asBigDecimal("AD_DIASCORRIDOSVENC") != null) {
	        int diasCorridos = parceiroVO.asBigDecimal("AD_DIASCORRIDOSVENC").intValue();
	        dataVencimento = hoje.plusDays(diasCorridos);
	    }
	    else {
	        dataVencimento = hoje.plusDays(15);
	    }
	    
	    dataVencimento = ajustarParaUltimoDiaDoMes(dataVencimento);
	    
	    return Timestamp.valueOf(dataVencimento.atStartOfDay());
	}

	private void atualizarDataVencimento(DynamicVO financeiroVO, Timestamp dataVencimento) {
	    try {
	        financeiroDAO.prepareToUpdate(financeiroVO)
	            .set("DTVENC", dataVencimento)
	            .update();
	    } catch (Exception e) {
	        System.out.println("Erro data de corte - Metropolitana/Gustavo: " + e.getMessage());
	        e.printStackTrace();
	    }
	}


	@Override
	public void afterUpdate(PersistenceEvent event) throws Exception {

		
	}

	@Override
	public void beforeCommit(TransactionContext arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void beforeDelete(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void beforeInsert(PersistenceEvent event) throws Exception {
		
	}
	
	private LocalDate ajustarParaUltimoDiaDoMes(LocalDate data) {
	    int diaOriginal = data.getDayOfMonth();
	    int ultimoDiaMes = data.lengthOfMonth();
	    
	    if (diaOriginal > ultimoDiaMes) {
	        return data.withDayOfMonth(ultimoDiaMes);
	    }
	    return data;
	}

	public boolean existefaturHH(BigDecimal nunota) throws Exception {
		JapeWrapper faturamentosHHDAO = JapeFactory.dao("AD_TABFATURAMENTO");
		if(nunota != null) {
			DynamicVO faturamentoHHVO = faturamentosHHDAO.findOne("NUNOTA = ?",nunota);
			
			if(faturamentoHHVO != null) {
				return true;
			}
		}
		return false;
		
	}

	@Override
	public void beforeUpdate(PersistenceEvent arg0) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
